package scala.tools.nsc.typechecker

import scala.meta.internal.pc.PresentationCompiler

trait ClassPathProxy { this: PresentationCompiler =>
  def metalsContainsPackage(pkg: String): Boolean =
    this.classPath.hasPackage(pkg)
  def metalsNewContext(c: Context): Context = {
    val context =
      new Context(c.tree, c.owner, c.scope, c.unit, c, c.reporter)
      with CustomIss
    context.enclClass = c.enclClass
    context.enclMethod = c.enclMethod
    context
  }
  trait CustomIss extends Context {
    override def implicitss: List[List[analyzer.ImplicitInfo]] = {
      val pkg = rootMirror.staticPackage("scala.collection")
      val jc = pkg.info.member(TermName("JavaConverters"))
      val seqAsJavaListConverter =
        jc.info.member(TermName("seqAsJavaListConverter"))
      seqAsJavaListConverter.info
      val info = new analyzer.ImplicitInfo(
        seqAsJavaListConverter.name,
        jc.tpe,
        seqAsJavaListConverter
      )
      info.tpe
      List(List(info))
    }
  }
}
