package scala.meta.internal.pc

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.language.implicitConversions
import scala.meta.internal.semanticdb.scalac.SemanticdbOps
import scala.meta.pc
import scala.meta.pc.MethodInformation
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolSearch
import scala.meta.pc.SymbolVisitor
import scala.reflect.internal.{Flags => gf}
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

  def isDocs: Boolean = System.getProperty("metals.signature-help") != "no-docs"

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
    val actualSymbol =
      if (!symbol.isJava && symbol.isPrimaryConstructor) symbol.owner
      else symbol
    val sym = compiler.semanticdbSymbol(actualSymbol)
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

  def inverseSemanticdbSymbol(symbol: String): Symbol = {
    import scala.meta.internal.semanticdb.Scala._
    def loop(s: String): Symbol = {
      if (s.isNone || s.isRootPackage) rootMirror.RootPackage
      else if (s.isEmptyPackage) rootMirror.EmptyPackage
      else {
        val (desc, parent) = DescriptorParser(s)
        val owner = loop(parent)
        pprint.log(owner)
        owner match {
          case NoSymbol =>
            NoSymbol
          case owner =>
            desc match {
              case Descriptor.None =>
                owner
              case Descriptor.Type(value) =>
                owner.info.member(TypeName(value))
              case Descriptor.Term(value) =>
                owner.info.member(TermName(value))
              case Descriptor.Package(value) =>
                owner.info.member(TermName(value))
              case Descriptor.Parameter(value) =>
                owner.paramss.flatten
                  .find(_.name.containsName(value))
                  .getOrElse(NoSymbol)
              case Descriptor.TypeParameter(value) =>
                owner.typeParams
                  .find(_.name.containsName(value))
                  .getOrElse(NoSymbol)
              case Descriptor.Method(value, _) =>
                owner.info
                  .member(TermName(value))
                  .alternatives
                  .iterator
                  .filter(sym => semanticdbSymbol(sym) == s)
                  .toList
                  .headOption
                  .getOrElse(NoSymbol)
            }
        }
      }
    }
    loop(symbol)
  }

  class SignaturePrinter(
      method: Symbol,
      shortenedNames: ShortenedNames,
      methodType: Type,
      includeDocs: Boolean
  ) {
    private val info =
      if (includeDocs) methodInfo(method)
      else None
    private val infoParamsA: Seq[pc.ParameterInformation] = info match {
      case Some(value) =>
        value.typeParameters().asScala ++
          value.parameters().asScala
      case None =>
        IndexedSeq.empty
    }
    private val infoParams =
      infoParamsA.lift
    private val returnType =
      metalsToLongString(methodType.finalResultType, shortenedNames)

    def methodDocstring: String = {
      if (isDocs) info.fold("")(_.docstring())
      else ""
    }
    def isTypeParameters: Boolean = methodType.typeParams.nonEmpty
    def isImplicit: Boolean = methodType.paramss.lastOption match {
      case Some(head :: _) => head.isImplicit
      case _ => false
    }
    def mparamss: List[List[Symbol]] =
      methodType.typeParams match {
        case Nil => methodType.paramss
        case tparams => tparams :: methodType.paramss
      }
    def defaultMethodSignature: String = {
      var i = 0
      val paramss = methodType.typeParams match {
        case Nil => methodType.paramss
        case tparams => tparams :: methodType.paramss
      }
      val params = paramss.iterator.map { params =>
        val labels = params.iterator.map { param =>
          val result = paramLabel(param, i)
          i += 1
          result
        }
        labels
      }
      methodSignature(params, name = "")
    }

    def methodSignature(
        paramLabels: Iterator[Iterator[String]],
        name: String = method.nameString
    ): String = {
      paramLabels
        .zip(mparamss.iterator)
        .map {
          case (params, syms) =>
            paramsKind(syms) match {
              case Params.TypeParameterKind =>
                params.mkString("[", ", ", "]")
              case Params.NormalKind =>
                params.mkString("(", ", ", ")")
              case Params.ImplicitKind =>
                params.mkString("(implicit ", ", ", ")")
            }
        }
        .mkString(name, "", s": ${returnType}")
    }
    def paramsKind(syms: List[Symbol]): Params.Kind = {
      syms match {
        case head :: _ =>
          if (head.isType) Params.TypeParameterKind
          else if (head.isImplicit) Params.ImplicitKind
          else Params.NormalKind
        case Nil => Params.NormalKind
      }
    }
    def paramDocstring(paramIndex: Int): String = {
      if (isDocs) infoParams(paramIndex).fold("")(_.docstring())
      else ""
    }
    def paramLabel(param: Symbol, index: Int): String = {
      val paramTypeString = metalsToLongString(param.info, shortenedNames)
      val name = infoParams(index) match {
        case Some(value) => value.name()
        case None => param.nameString
      }
      if (param.isTypeParameter) {
        name + paramTypeString
      } else {
        val default =
          if (param.isParamWithDefault) {
            val defaultValue = infoParams(index).map(_.defaultValue()) match {
              case Some(value) if !value.isEmpty => value
              case _ => "{}"
            }
            s" = $defaultValue"
          } else {
            ""
          }
        s"$name: ${paramTypeString}$default"
      }
    }
  }

  def addCompilationUnit(
      code: String,
      filename: String,
      cursor: Option[Int],
      cursorName: String = "_CURSOR_"
  ): RichCompilationUnit = {
    val codeWithCursor = cursor match {
      case Some(offset) =>
        code.take(offset) + cursorName + code.drop(offset)
      case _ => code
    }
    val unit = newCompilationUnit(codeWithCursor, filename)
    val richUnit = new RichCompilationUnit(unit.source)
    unitOfFile(richUnit.source.file) = richUnit
    richUnit
  }

}
