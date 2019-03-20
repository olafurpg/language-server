package scala.meta.internal.pc

import org.eclipse.lsp4j.jsonrpc.messages.{Either => JEither}
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkedString
import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.pc.OffsetParams
import scala.reflect.internal.{Flags => gf}

class HoverProvider(val compiler: MetalsGlobal, params: OffsetParams) {
  import compiler._
  def hover(): Option[Hover] = {
    if (params.offset() < 0 ||
      params.offset() >= params.text().length ||
      params.text().charAt(params.offset()).isWhitespace) {
      None
    } else {
      val unit = addCompilationUnit(
        code = params.text(),
        filename = params.filename(),
        cursor = None
      )
      val pos = unit.position(params.offset())
      val tree = compiler.typedTreeAt(pos)
      val NamedArgument = new NamedArgument(pos)
      tree match {
        case NamedArgument(hover) =>
          Some(hover)
        case _: Import | _: Select | _: Apply | _: TypeApply | _: Ident =>
          val expanded = expandRange(pos)
          if (expanded != null &&
            expanded.tpe != null &&
            tree.symbol != null) {
            val symbol =
              if (expanded.symbol.isConstructor) expanded.symbol
              else tree.symbol
            toHover(
              symbol,
              symbol.keyString,
              seenFromType(tree, symbol),
              expanded.tpe,
              pos,
              expanded.pos
            )
          } else {
            for {
              sym <- Option(tree.symbol)
              tpe <- Option(tree.tpe)
              seenFrom = seenFromType(tree, sym)
              hover <- toHover(sym, sym.keyString, seenFrom, tpe, pos, tree.pos)
            } yield hover
          }
        case UnApply(fun, _) if fun.symbol != null =>
          toHover(
            fun.symbol,
            fun.symbol.keyString,
            seenFromType(tree, fun.symbol),
            tree.tpe,
            pos,
            pos
          )
        case v: ValOrDefDef if v.namePos.includes(pos) && v.symbol != null =>
          val symbol = (v.symbol.getter: Symbol) match {
            case NoSymbol => v.symbol
            case getter => getter
          }
          toHover(
            symbol,
            v.symbol.keyString,
            symbol.info,
            symbol.info,
            pos,
            v.pos
          )
        case _ =>
          // Don't show hover for non-identifiers.
          None
      }
    }
  }

  def seenFromType(tree0: Tree, symbol: Symbol): Type = {
    def qual(t: Tree): Tree = t match {
      case TreeApply(q, _) => qual(q)
      case Select(q, _) => q
      case Import(q, _) => q
      case t => t
    }
    val tree = qual(tree0)
    val pre = stabilizedType(tree)
    val memberType = pre.memberType(symbol)
    if (memberType.isErroneous) symbol.info
    else memberType
  }

  def expandRange(pos: Position): Tree = {
    def tryTail(enclosing: List[Tree]): Option[Tree] = enclosing match {
      case Nil => None
      case head :: tail =>
        val x = head match {
          case TreeApply(qual, _) if qual.pos.includes(pos) =>
            tryTail(tail).orElse(Some(head))
          case New(_) =>
            tail match {
              case Nil => None
              case Select(_, _) :: next =>
                tryTail(next)
              case _ =>
                None
            }
          case _ =>
            None
        }
        x
    }
    lastEnclosing match {
      case head :: tail =>
        tryTail(tail) match {
          case Some(value) =>
            typedTreeAt(value.pos)
          case None =>
            head
        }
      case _ =>
        EmptyTree
    }
  }

  def toHover(
      symbol: Symbol,
      keyword: String,
      seenFrom: Type,
      tpe: Type,
      pos: Position,
      range: Position
  ): Option[Hover] = {
    if (tpe == null || tpe.isErroneous || tpe == NoType) None
    else if (symbol == null || symbol == NoSymbol || symbol.isErroneous) None
    else if (symbol.info.finalResultType.isInstanceOf[ClassInfoType]) None
    else {
      val context = doLocateContext(pos)
      def widen(t: Type): Type =
        if (symbol.isLocallyDefinedSymbol) {
          // NOTE(olafur) Dealias type for local symbols to avoid unwanted `x.type` singleton
          // types for cases like:
          //   for (x <- List(1); if @@x > 1) println(x)
          // We might want to refine this heuristic down the road to either widen more aggressively
          // or less aggressively as we gain more experience.
          t.widen
        } else {
          t
        }
      val history = new ShortenedNames(
        lookupSymbol = name => context.lookupSymbol(name, _ => true) :: Nil
      )
      val symbolInfo =
        if (seenFrom.isErroneous) symbol.info
        else seenFrom
      val printer = new SignaturePrinter(
        symbol,
        history,
        widen(symbolInfo),
        includeDocs = false
      )
      val name =
        if (symbol.isConstructor) "this"
        else symbol.name.decoded
      val flags = List(symbolFlagString(symbol), keyword, name)
        .filterNot(_.isEmpty)
        .mkString(" ")
      val prettyType = metalsToLongString(widen(tpe).finalResultType, history)
      val macroSuffix =
        if (symbol.isMacro) " = macro"
        else ""
      val prettySignature = printer.defaultMethodSignature(flags) + macroSuffix
      val contents = new java.util.ArrayList[JEither[String, MarkedString]](2)

      contents.add(
        JEither.forRight[String, MarkedString](
          new MarkedString("scala", prettySignature)
        )
      )

      val needsPrettyType = !symbol.isConstructor && {
        !symbol.paramss.isEmpty ||
        !symbol.typeParams.isEmpty ||
        !prettySignature.contains(prettyType)
      }
      if (needsPrettyType) {
        contents.add(
          JEither.forRight[String, MarkedString](
            new MarkedString("scala", prettyType)
          )
        )
      }
      val hover = new Hover(contents)
      if (range.isRange) {
        hover.setRange(range.toLSP)
      }
      Some(hover)
    }
  }

  class NamedArgument(pos: Position) {
    // Special case for named arguments like `until(en@@d = 10)`, in which case
    // we fallback to signature help to extract the named argument parameter.
    def unapply(a: Apply): Option[Hover] = a match {
      case Apply(qual, _) if !qual.pos.includes(pos) && !isForSynthetic(a) =>
        val signatureHelp =
          new SignatureHelpProvider(compiler).signatureHelp(params)
        if (!signatureHelp.getSignatures.isEmpty &&
          signatureHelp.getActiveParameter >= 0 &&
          signatureHelp.getActiveSignature >= 0) {
          val activeParameter = signatureHelp.getSignatures
            .get(signatureHelp.getActiveSignature)
            .getParameters
            .get(signatureHelp.getActiveParameter)

          val contents =
            new java.util.ArrayList[JEither[String, MarkedString]]()
          contents.add(
            JEither.forRight(
              new MarkedString("scala", activeParameter.getLabel)
            )
          )
          Option(activeParameter.getDocumentation).foreach { documentation =>
            documentation.asScala match {
              case Left(value) =>
                contents.add(JEither.forLeft(value))
              case Right(value) =>
                contents.add(
                  JEither
                    .forRight(new MarkedString(value.getKind, value.getValue))
                )
            }
          }
          Some(new Hover(contents))
        } else {
          None
        }
      case _ =>
        None
    }
    val isForName = Set[Name](
      nme.map,
      nme.withFilter,
      nme.flatMap,
      nme.foreach
    )
    private def isForSynthetic(gtree: Tree): Boolean = {
      def isForComprehensionSyntheticName(select: Select): Boolean = {
        select.pos == select.qualifier.pos && isForName(select.name)
      }
      gtree match {
        case Apply(fun, List(_: Function)) => isForSynthetic(fun)
        case TypeApply(fun, _) => isForSynthetic(fun)
        case gtree: Select if isForComprehensionSyntheticName(gtree) => true
        case _ => false
      }
    }
  }

  def symbolFlagString(sym: Symbol): String = {
    var mask = sym.flagMask
    // Strip case modifier off non-class symbols like synthetic apply/copy.
    if (sym.isCase && !sym.isClass) mask &= ~gf.CASE
    sym.flagString(mask)
  }
}
