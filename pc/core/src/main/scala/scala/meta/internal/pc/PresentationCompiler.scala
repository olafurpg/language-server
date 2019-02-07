package scala.meta.internal.pc

import java.nio.CharBuffer
import scala.collection.concurrent.TrieMap
import scala.language.implicitConversions
import scala.meta.internal.metals.ClasspathSearch
import scala.meta.internal.semanticdb.scalac.SemanticdbOps
import scala.meta.pc.MethodInformation
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolVisitor
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.Reporter

class PresentationCompiler(
    settings: Settings,
    reporter: Reporter,
    val indexer: SymbolIndexer,
    val search: ClasspathSearch
) extends Global(settings, reporter) { compiler =>

  lazy val semanticdbOps: SemanticdbOps {
    val global: compiler.type
  } = new SemanticdbOps {
    val global: compiler.type = compiler
  }

  def semanticdbSymbol(symbol: Symbol): String = {
    import semanticdbOps._
    symbol.toSemantic
  }

  def printPretty(pos: Position): Unit = {
    println(pretty(pos))
  }
  def pretty(pos: Position): String = {
    if (pos.isDefined) {
      val lineCaret =
        if (pos.isRange) {
          val indent = " " * (pos.column - 1)
          val caret = "^" * (pos.end - pos.start)
          indent + caret
        } else {
          pos.lineCaret
        }
      pos.lineContent + "\n" + lineCaret
    } else {
      "<none>"
    }
  }

  def treePos(tree: Tree): Position = {
    if (tree.pos == null) {
      NoPosition
    } else if (tree.symbol != null &&
      tree.symbol.name.startsWith("x$") &&
      tree.symbol.isArtifact) {
      tree.symbol.pos
    } else {
      tree.pos
    }
  }

  def methodInfo(symbol: Symbol): Option[MethodInformation] = {
    val sym = compiler.semanticdbSymbol(symbol)
    methodInfos.get(sym) match {
      case Some(null) => None
      case s: Some[t] => s
      case None =>
        index(sym)
        val result = methodInfos.get(sym)
        if (result.isEmpty) {
          methodInfos.put(sym, null)
        }
        result
    }
  }

  def index(symbol: String): Unit = {
    indexer.visit(
      symbol,
      new SymbolVisitor {
        override def visitMethod(method: MethodInformation): Unit = {
          methodInfos(method.symbol()) = method
        }
      }
    )
  }

  val methodInfos = TrieMap.empty[String, MethodInformation]

  // Only needed for 2.11 where `Name` doesn't extend CharSequence.
  implicit def nameToCharSequence(name: Name): CharSequence =
    name.toString

}
