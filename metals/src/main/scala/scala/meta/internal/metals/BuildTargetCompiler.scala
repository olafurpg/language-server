package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.ScalaBuildTarget
import ch.epfl.scala.bsp4j.ScalacOptionsItem
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.pc.ScalaPC
import scala.meta.pc.PC
import scala.meta.pc.SymbolIndexer
import scala.util.Properties

case class BuildTargetCompiler(pc: PC, search: ClasspathSearch)
    extends Cancelable {
  override def cancel(): Unit = pc.shutdown()
}

object BuildTargetCompiler {
  def fromClasspath(
      scalac: ScalacOptionsItem,
      info: ScalaBuildTarget,
      indexer: SymbolIndexer
  ): BuildTargetCompiler = {
    if (info.getScalaVersion != Properties.versionNumberString) {
      throw new IllegalArgumentException(info.toString)
    }
    val classpath = scalac.classpath.map(_.toNIO).toSeq
    // TODO(olafur) reuse workspace/symbol or cache per jar
    val search = ClasspathSearch.fromClasspath(classpath, _ => 0)
    // TODO(olafur) match exact scala version
    val pc = new ScalaPC(classpath, scalac.getOptions.asScala, indexer, search)
    BuildTargetCompiler(pc, search)
  }
}
