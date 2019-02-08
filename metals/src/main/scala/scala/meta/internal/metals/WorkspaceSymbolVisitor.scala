package scala.meta.internal.metals

import org.eclipse.lsp4j.jsonrpc.CancelChecker
import org.eclipse.{lsp4j => l}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.mtags.SymbolDefinition
import scala.meta.internal.semanticdb.Scala.Descriptor
import scala.meta.internal.semanticdb.Scala.Symbols
import scala.meta.io.AbsolutePath
import scala.meta.pc.SymbolSearchVisitor

class WorkspaceSymbolVisitor(
    query: WorkspaceSymbolQuery,
    token: CancelChecker,
    index: OnDemandSymbolIndex,
    fileOnDisk: AbsolutePath => AbsolutePath
) extends SymbolSearchVisitor {
  val classpathEntries = ArrayBuffer.empty[l.SymbolInformation]
  val isVisited = mutable.Set.empty[AbsolutePath]
  def definition(
      pkg: String,
      filename: String,
      index: OnDemandSymbolIndex
  ): Option[SymbolDefinition] = {
    val nme = Classfile.name(filename)
    val tpe = Symbol(Symbols.Global(pkg, Descriptor.Type(nme)))
    index.definition(tpe).orElse {
      val term = Symbol(Symbols.Global(pkg, Descriptor.Term(nme)))
      index.definition(term)
    }
  }
  override def preVisitPackage(pkg: String): Boolean = true
  override def visitClassfile(pkg: String, filename: String): Unit = {
    for {
      defn <- definition(pkg, filename, index)
      if !isVisited(defn.path)
    } {
      val input = defn.path.toInput
      lazy val uri = fileOnDisk(defn.path).toURI.toString
      SemanticdbDefinition.foreach(input) { defn =>
        if (query.matches(defn.info)) {
          classpathEntries += defn.toLSP(uri)
        }
      }
    }
  }
  override def isCancelled: Boolean = token.isCancelled
}
