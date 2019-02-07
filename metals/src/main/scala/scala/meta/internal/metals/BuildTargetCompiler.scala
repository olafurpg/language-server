package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.ScalacOptionsItem
import scala.meta.pc.PC
import MetalsEnrichments._
import scala.meta.internal.pc.ScalaPC
import scala.meta.pc.SymbolIndexer

case class BuildTargetCompiler(pc: PC, search: ClasspathSearch)
    extends Cancelable {
  override def cancel(): Unit = pc.shutdown()
}

object BuildTargetCompiler {
  def fromClasspath(
      scalac: ScalacOptionsItem,
      indexer: SymbolIndexer
  ): BuildTargetCompiler = {
    val classpath = scalac.classpath.map(_.toNIO).toSeq
    // TODO(olafur) reuse workspace/symbol or cache per jar
    val search = ClasspathSearch.fromClasspath(classpath, _ => 0)
    // TODO(olafur) match exact scala version
    val pc = new ScalaPC(classpath, scalac.getOptions.asScala, indexer, search)
    BuildTargetCompiler(pc, search)
  }
}
