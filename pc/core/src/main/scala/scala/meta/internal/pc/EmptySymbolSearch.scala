package scala.meta.internal.pc

import scala.meta.pc.SymbolSearch
import scala.meta.pc.SymbolSearchVisitor

object EmptySymbolSearch extends SymbolSearch {
  override def search(query: String, visitor: SymbolSearchVisitor): Unit = {}
}
