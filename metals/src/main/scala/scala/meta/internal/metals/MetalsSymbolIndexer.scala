package scala.meta.internal.metals

import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.semanticdb.Language
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolVisitor

class MetalsSymbolIndexer(index: OnDemandSymbolIndex) extends SymbolIndexer {
  override def visit(symbol: String, visitor: SymbolVisitor): Unit = {
    index.definition(Symbol(symbol)) match {
      case Some(defn) =>
        defn.path.toLanguage match {
          case Language.JAVA =>
            new JavaSymbolIndexer(defn.path.toInput).visit(symbol, visitor)
          case Language.SCALA =>
            val input = defn.path.toInput
            visitor.visitInput(input.path, input.value)
          case _ =>
        }
      case None =>
    }

  }
}
