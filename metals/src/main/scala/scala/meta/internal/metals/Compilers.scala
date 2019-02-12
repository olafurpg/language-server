package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.jsonrpc.CancelChecker
import scala.collection.concurrent.TrieMap
import scala.meta.inputs.Position
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.pc.PC
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolSearch

class Compilers(
    buildTargets: BuildTargets,
    buffers: Buffers,
    indexer: SymbolIndexer,
    search: SymbolSearch
) extends Cancelable {

  private val cache = TrieMap.empty[BuildTargetIdentifier, BuildTargetCompiler]
  override def cancel(): Unit = {
    Cancelable.cancelAll(cache.values)
    cache.clear()
  }
  def didCompileSuccessfully(id: BuildTargetIdentifier): Unit = {
    cache.remove(id).foreach(_.cancel())
  }

  def completionItemResolve(
      item: CompletionItem,
      token: CancelChecker
  ): Option[CompletionItem] = {
    for {
      data <- item.data
      compiler <- cache.get(new BuildTargetIdentifier(data.target))
    } yield compiler.pc.completionItemResolve(item, data.symbol)
  }
  def completions(
      params: CompletionParams,
      token: CancelChecker
  ): Option[CompletionList] =
    withPC(params) { (pc, pos) =>
      pc.complete(
        CompilerOffsetParams(pos.input.syntax, pos.input.text, pos.start, token)
      )
    }
  def hover(
      params: TextDocumentPositionParams,
      token: CancelChecker
  ): Option[Hover] =
    withPC(params) { (pc, pos) =>
      pc.hover(
        CompilerOffsetParams(pos.input.syntax, pos.input.text, pos.start, token)
      )
    }
  def signatureHelp(
      params: TextDocumentPositionParams,
      token: CancelChecker
  ): Option[SignatureHelp] =
    withPC(params) { (pc, pos) =>
      pc.signatureHelp(
        CompilerOffsetParams(pos.input.syntax, pos.input.text, pos.start, token)
      )
    }

  private def withPC[T](
      params: TextDocumentPositionParams
  )(fn: (PC, Position) => T): Option[T] = {
    val path = params.getTextDocument.getUri.toAbsolutePath
    for {
      target <- buildTargets.inverseSources(path)
      info <- buildTargets.info(target)
      scala <- info.asScalaBuildTarget
      scalac <- buildTargets.scalacOptions(target)
    } yield {
      val compiler = cache.getOrElseUpdate(
        target,
        BuildTargetCompiler.fromClasspath(scalac, scala, indexer, search)
      )
      val input = path.toInputFromBuffers(buffers)
      val pos = params.getPosition.toMeta(input)
      val result = fn(compiler.pc, pos)
      result
    }
  }
}
