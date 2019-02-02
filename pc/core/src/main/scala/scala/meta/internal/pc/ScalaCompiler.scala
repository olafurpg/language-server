package scala.meta.internal.pc

import scala.collection.concurrent.TrieMap
import scala.meta.internal.semanticdb.scalac.SemanticdbOps
import scala.meta.pc.MethodInformation
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolVisitor
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.reporters.Reporter

class ScalaCompiler(
    settings: Settings,
    reporter: Reporter,
    indexer: SymbolIndexer
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

  def methodInfo(symbol: Symbol): Option[MethodInformation] = {
    val sym = compiler.semanticdbSymbol(symbol)
    methodInfos.get(sym).orElse {
      if (!symbol.isJava) None
      else {
        index(sym)
        methodInfos.get(sym)
      }
    }
  }

  def index(symbol: String): Unit = {
    indexer.visit(
      symbol,
      new SymbolVisitor {
        override def visitInput(filename: String, text: String): Unit = {
          if (filename.endsWith(".scala")) {
            pprint.log(filename)
          }
        }
        override def visitMethod(method: MethodInformation): Unit = {
          methodInfos(method.symbol()) = method
        }
      }
    )
  }

  val methodInfos = TrieMap.empty[String, MethodInformation]

}
