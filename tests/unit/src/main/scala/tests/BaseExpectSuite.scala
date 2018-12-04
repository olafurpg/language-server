package tests

import scala.meta.internal.metals.JdkClasspath
import scala.meta.internal.mtags.SemanticdbClasspath
import scala.meta.internal.symtab.GlobalSymbolTable
import scala.meta.io.AbsolutePath

/** Base class for all expect tests.
 *
 * Exposes useful methods to lookup metadata about the input project.
 */
abstract class BaseExpectSuite(val suiteName: String) extends BaseSuite {
  lazy val input = InputProperties.default()

  lazy val symtab = {
    GlobalSymbolTable(
      input.classpath ++ JdkClasspath.bootClasspath
    )
  }
  final lazy val sourceroot: AbsolutePath =
    AbsolutePath(BuildInfo.sourceroot)
  final lazy val classpath =
    new SemanticdbClasspath(sourceroot, input.classpath)
  def saveExpect(): Unit
}
