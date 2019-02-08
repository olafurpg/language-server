package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.ScalaBuildTarget
import ch.epfl.scala.bsp4j.ScalacOptionsItem
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.pc.ScalaPC
import scala.meta.pc.PC
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolSearch
import scala.util.Properties

case class BuildTargetCompiler(pc: PC, search: SymbolSearch)
    extends Cancelable {
  override def cancel(): Unit = pc.shutdown()
}

object BuildTargetCompiler {
  def fromClasspath(
      scalac: ScalacOptionsItem,
      info: ScalaBuildTarget,
      indexer: SymbolIndexer,
      search: SymbolSearch
  ): BuildTargetCompiler = {
    if (info.getScalaVersion != Properties.versionNumberString) {
      throw new IllegalArgumentException(info.toString)
    }
    val classpath = scalac.classpath.map(_.toNIO).toSeq
    // TODO(olafur) match exact scala version
    val pc = new ScalaPC(classpath, scalac.getOptions.asScala, indexer, search)
    BuildTargetCompiler(pc, search)
  }
}
