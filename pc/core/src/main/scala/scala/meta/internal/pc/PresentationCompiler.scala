package scala.meta.internal.pc

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.language.implicitConversions
import scala.meta.internal.metals.ClasspathSearch
import scala.meta.internal.semanticdb.TypeMessage.SealedValue.ByNameType
import scala.meta.internal.semanticdb.scalac.SemanticdbOps
import scala.reflect.internal.{Flags => gf}
import scala.meta.pc.MethodInformation
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolSearch
import scala.meta.pc.SymbolVisitor
import scala.tools.nsc.Settings
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.metals.ClassPathProxy
import scala.tools.nsc.reporters.Reporter

class PresentationCompiler(
    settings: Settings,
    reporter: Reporter,
    val indexer: SymbolIndexer,
    val search: SymbolSearch,
    val buildTargetIdentifier: String
) extends Global(settings, reporter)
    with ClassPathProxy { compiler =>

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

  def metalsToLongString(
      tpe: Type,
      history: ShortenedNames
  ): String = {
    // The following pattern match is an adaptation of this pattern match:
    // https://github.com/scalameta/scalameta/blob/dc639c83f1c26627c39aef9bfb3dae779ecdb237/semanticdb/scalac/library/src/main/scala/scala/meta/internal/semanticdb/scalac/TypeOps.scala
    def shortType(tpe: Type, name: Option[Name]): Type = tpe match {
      case TypeRef(pre, sym, args) =>
        TypeRef(
          shortType(pre, Some(sym.name)),
          sym,
          args.map(arg => shortType(arg, name))
        )
      case SingleType(pre, sym) =>
        if (sym.hasPackageFlag) {
          if (history.tryShortenName(name, sym)) NoPrefix
          else tpe
        } else {
          SingleType(shortType(pre, Some(sym.name)), sym)
        }
      case ThisType(sym) =>
        if (sym.hasPackageFlag) {
          if (history.tryShortenName(name, sym)) NoPrefix
          else tpe
        } else {
          TypeRef(NoPrefix, sym, Nil)
        }
      case ConstantType(Constant(sym: TermSymbol))
          if sym.hasFlag(gf.JAVA_ENUM) =>
        shortType(SingleType(sym.owner.thisPrefix, sym), None)
      case ConstantType(Constant(tpe: Type)) =>
        ConstantType(Constant(shortType(tpe, None)))
      case SuperType(thistpe, supertpe) =>
        SuperType(shortType(thistpe, None), shortType(supertpe, None))
      case RefinedType(parents, decls) =>
        RefinedType(parents.map(parent => shortType(parent, None)), decls)
      case AnnotatedType(annotations, underlying) =>
        AnnotatedType(annotations, shortType(underlying, None))
      case ExistentialType(quantified, underlying) =>
        ExistentialType(quantified, shortType(underlying, None))
      case PolyType(tparams, resultType) =>
        PolyType(tparams, resultType.map(t => shortType(t, None)))
      case t => t
    }
    shortType(tpe, None).toLongString
  }

//  def metalsToShortString(tpe: Type): String = {
//    val sb = new StringBuilder()
//    def loop(t: Type): Unit = {}
//    sb.toString()
//  }

  val methodInfos = TrieMap.empty[String, MethodInformation]

  // Only needed for 2.11 where `Name` doesn't extend CharSequence.
  implicit def nameToCharSequence(name: Name): CharSequence =
    name.toString

  class ShortenedNames(history: mutable.Map[Name, Symbol] = mutable.Map.empty) {
    def tryShortenName(name: Option[Name], sym: Symbol): Boolean =
      name match {
        case Some(n) =>
          history.get(n) match {
            case Some(other) =>
              if (other == sym) true
              else false
            case _ =>
              history(n) = sym
              true
          }
        case _ =>
          false
      }
  }

}
