package scala.meta.internal.metals

import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.mtags.Symbol
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolVisitor

class MetalsSymbolIndexer(index: OnDemandSymbolIndex) extends SymbolIndexer {
  override def visit(symbol: String, visitor: SymbolVisitor): Unit = {
    index.definition(Symbol(symbol)) match {
      case Some(defn) =>
        if (defn.path.extension == "java") {
          new JavaSymbolIndexer(defn.path.toInput).visit(symbol, visitor)
        }
      case None =>
    }

  }
}
