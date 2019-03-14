package scala.meta.internal.pc

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.meta.pc
import scala.meta.pc.SymbolDocumentation

trait Signatures { this: MetalsGlobal =>

  case class ShortName(
      name: Name,
      symbol: Symbol
  ) {
    def isRename: Boolean = symbol.name != name
    def asImport: String = {
      val ident = Identifier(name)
      if (isRename) s"${Identifier(symbol.name)} => ${ident}"
      else ident
    }
    def owner: Symbol = symbol.owner
  }
  object ShortName {
    def apply(sym: Symbol): ShortName =
      ShortName(sym.name, sym)
  }

  class ShortenedNames(
      val history: mutable.Map[Name, ShortName] = mutable.Map.empty,
      lookupSymbol: Name => NameLookup = _ => LookupNotFound,
      val config: Map[Symbol, Name] = Map.empty
  ) {
    def this(context: Context, config: Map[Symbol, Name]) =
      this(lookupSymbol = { name =>
        context.lookupSymbol(name, _ => true)
      }, config = config)
    def nameResolvesToSymbol(name: Name, sym: Symbol): Boolean = {
      lookupSymbol(name) match {
        case LookupNotFound => true
        case l => sym.isKindaTheSameAs(l.symbol)
      }
    }
    def tryShortenName(short: ShortName): Boolean = {
      val ShortName(name, sym) = short
      history.get(name) match {
        case Some(ShortName(_, other)) =>
          if (other.isKindaTheSameAs(sym)) true
          else false
        case _ =>
          val isOk = lookupSymbol(name) match {
            case LookupSucceeded(_, symbol) =>
              symbol.isKindaTheSameAs(sym)
            case LookupNotFound =>
              true
            case _ =>
              false
          }
          if (isOk) {
            history(name) = short
            true
          } else {
            false // conflict, do not shorten name.
          }
      }
    }
    def tryShortenName(name: Option[ShortName]): Boolean =
      name match {
        case Some(short) =>
          tryShortenName(short)
        case _ =>
          false
      }
  }

  class SignaturePrinter(
      gsym: Symbol,
      shortenedNames: ShortenedNames,
      gtpe: Type,
      includeDocs: Boolean
  ) {
    private val info: Option[SymbolDocumentation] =
      if (includeDocs) {
        symbolDocumentation(gsym)
      } else {
        None
      }
    private val infoParamsA: Seq[pc.SymbolDocumentation] = info match {
      case Some(value) =>
        value.typeParameters().asScala ++
          value.parameters().asScala
      case None =>
        IndexedSeq.empty
    }
    private val infoParams =
      infoParamsA.lift
    private val returnType =
      metalsToLongString(gtpe.finalResultType, shortenedNames)

    def methodDocstring: String = {
      if (isDocs) info.fold("")(_.docstring())
      else ""
    }
    def isTypeParameters: Boolean = gtpe.typeParams.nonEmpty
    def implicitParams: Option[List[Symbol]] =
      gtpe.paramss.lastOption.filter(_.headOption.exists(_.isImplicit))
    val implicitEvidencesByTypeParam
        : collection.Map[Symbol, ListBuffer[String]] = {
      val result = mutable.Map.empty[Symbol, ListBuffer[String]]
      for {
        param <- implicitParams.getOrElse(Nil).iterator
        if param.name.startsWith(termNames.EVIDENCE_PARAM_PREFIX)
        TypeRef(
          _,
          sym,
          TypeRef(NoPrefix, tparam, Nil) :: Nil
        ) <- List(param.info)
      } {
        val buf = result.getOrElseUpdate(tparam, ListBuffer.empty)
        buf += sym.name.toString
      }
      result
    }
    def isImplicit: Boolean = implicitParams.isDefined
    def mparamss: List[List[Symbol]] =
      gtpe.typeParams match {
        case Nil => gtpe.paramss
        case tparams => tparams :: gtpe.paramss
      }
    def defaultMethodSignature(name: String = ""): String = {
      var i = 0
      val paramss = gtpe.typeParams match {
        case Nil => gtpe.paramss
        case tparams => tparams :: gtpe.paramss
      }
      val params = paramss.iterator.flatMap { params =>
        val labels = params.flatMap { param =>
          if (param.name.startsWith(termNames.EVIDENCE_PARAM_PREFIX)) {
            Nil
          } else {
            val result = paramLabel(param, i)
            i += 1
            result :: Nil
          }
        }
        if (labels.isEmpty && params.nonEmpty) Nil
        else labels.iterator :: Nil
      }
      methodSignature(params, name)
    }

    def methodSignature(
        paramLabels: Iterator[Iterator[String]],
        name: String = gsym.nameString
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
        case Some(value) if param.name.startsWith("x$") =>
          value.displayName()
        case _ => param.nameString
      }
      if (param.isTypeParameter) {
        val contextBounds =
          implicitEvidencesByTypeParam.getOrElse(param, Nil) match {
            case Nil => ""
            case head :: Nil => s":$head"
            case many => many.mkString(": ", ": ", "")
          }
        s"$name$paramTypeString$contextBounds"
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
}
