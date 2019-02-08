package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.TextDocumentPositionParams
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

  def completions(params: CompletionParams): Option[CompletionList] =
    withPC(params) { (pc, pos) =>
      pc.complete(pos.input.syntax, pos.input.text, pos.start)
    }
  def hover(params: TextDocumentPositionParams): Option[Hover] =
    withPC(params) { (pc, pos) =>
      pc.hover(pos.input.syntax, pos.input.text, pos.start)
    }
  def signatureHelp(params: TextDocumentPositionParams): Option[SignatureHelp] =
    withPC(params) { (pc, pos) =>
      pc.signatureHelp(pos.input.syntax, pos.input.text, pos.start)
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
