package scala.meta.internal.pc

import org.eclipse.lsp4j.jsonrpc.messages.{Either => JEither}
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkedString
import scala.tools.nsc.interactive.Global
import scala.collection.JavaConverters._

class HoverProvider(compiler: Global) {
  import compiler._
  def hover(filename: String, text: String, offset: Int): Option[Hover] = {
    val unit = ScalaPC.addCompilationUnit(
      global = compiler,
      code = text,
      filename = filename,
      cursor = None
    )
    val pos = unit.position(offset)
    val typedTree = compiler.typedTreeAt(pos)
    for {
      tpeName <- typeOfTree(typedTree)
    } yield
      new Hover(
        List(
          JEither.forRight[String, MarkedString](
            new MarkedString("scala", tpeName)
          )
        ).asJava
      )
  }

  private def typeOfTree(t: Tree): Option[String] = {
    val stringOrTree = t match {
      case t: DefDef => Right(t.symbol.asMethod.info.toLongString)
      case t: ValDef if t.tpt != null => Left(t.tpt)
      case t: ValDef if t.rhs != null => Left(t.rhs)
      case x => Left(x)
    }

    stringOrTree match {
      case Right(string) => Some(string)
      case Left(null) => None
      case Left(tree)
          if tree.tpe != null &&
            tree.tpe != NoType &&
            !tree.tpe.isErroneous =>
        Some(tree.tpe.widen.toString)
      case _ => None
    }

  }
}
