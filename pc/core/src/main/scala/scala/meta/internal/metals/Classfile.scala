package scala.meta.internal.metals

import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.mtags.SymbolDefinition
import scala.meta.internal.semanticdb.Scala.Descriptor
import scala.meta.internal.semanticdb.Scala.Symbols

case class Classfile(pkg: String, filename: String) {
  def isExact(query: WorkspaceSymbolQuery): Boolean =
    name == query.query
  def name: String = Classfile.name(filename)
}

object Classfile {
  def name(filename: String): String = {
    val dollar = filename.indexOf('$')
    if (dollar < 0) filename.stripSuffix(".class")
    else filename.substring(0, dollar)
  }
}
