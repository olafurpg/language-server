package scala.tools.nsc.metals

import scala.meta.internal.pc.PresentationCompiler

trait ClassPathProxy { this: PresentationCompiler =>
  def metalsContainsPackage(pkg: String): Boolean =
    this.classPath.hasPackage(pkg)
}
