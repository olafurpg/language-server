package scala.meta.internal.pc

import java.util.Comparator
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.meta.pc.CompletionItems
import scala.meta.pc.CompletionItems.LookupKind

class CompletionProvider(val compiler: ScalaCompiler) {
  import compiler._

  def completions(
      filename: String,
      text: String,
      offset: Int
  ): CompletionItems = {
    val unit = ScalaPC.addCompilationUnit(
      global = compiler,
      code = text,
      filename = filename,
      cursor = Some(offset)
    )
    val position = unit.position(offset)
    val (qual, kind, results) = safeCompletionsAt(position)
    val items = results.sorted(byRelevance).iterator.zipWithIndex.map {
      case (r, idx) =>
        val label = r.symNameDropLocal.decoded
        val item = new CompletionItem(label)
        item.setPreselect(true)
        val detail = qual match {
          case Some(tpe) =>
            // Compute type parameters based on the qualifier.
            // Example: Map[Int, String].applyOrE@@
            // Before: getOrElse[V1 >: V]     (key: K,   default: => V1): V1
            // After:  getOrElse[V1 >: String](key: Int, default: => V1): V1
            r.sym.infoString(tpe.memberType(r.sym))
          case _ =>
            if (r.sym.isClass || r.sym.isModuleOrModuleClass) {
              " " + semanticdbSymbol(r.sym.owner)
            } else {
              r.sym.signatureString
            }
        }
        item.setDetail(detail)
        item.setKind(completionItemKind(r))
        item.setSortText(f"${idx}%05d")
        item
    }
    new CompletionItems(kind, items.toSeq.asJava)
  }

  private def filterInteresting(completions: List[Member]): List[Member] = {
    val isUninterestingSymbol = Set[Symbol](
      // the methods == != ## are arguably "interesting" but they're here becuase
      // - they're short so completing them doesn't save you keystrokes
      // - they're available on everything so you
      definitions.Any_==,
      definitions.Any_!=,
      definitions.Any_##,
      definitions.Object_==,
      definitions.Object_!=,
      definitions.Object_##,
      definitions.Object_eq,
      definitions.Object_ne,
      definitions.RepeatedParamClass,
      definitions.ByNameParamClass,
      definitions.JavaRepeatedParamClass,
      definitions.Object_notify,
      definitions.Object_notifyAll,
      definitions.Object_notify,
      definitions.getMemberMethod(definitions.ObjectClass, termNames.wait_),
      definitions.getMemberMethod(
        definitions.getMemberClass(
          definitions.PredefModule,
          TypeName("ArrowAssoc")
        ),
        TermName("→").encode
      )
    ).flatMap(_.alternatives)
    def isSynthetic(sym: Symbol): Boolean = {
      sym.isJava && sym.isModuleOrModuleClass
    }
    val isSeen = mutable.Set.empty[String]
    val buf = List.newBuilder[Member]
    def loop(lst: List[Member]): Unit = lst match {
      case Nil =>
      case head :: tail =>
        val id =
          if (head.sym.isClass || head.sym.isModule) {
            head.sym.fullName
          } else {
            semanticdbSymbol(head.sym)
          }
        if (!isSeen(id) &&
          !isUninterestingSymbol(head.sym) &&
          !isSynthetic(head.sym)) {
          isSeen += id
          buf += head
        }
        loop(tail)
    }
    loop(completions)
    buf.result()
  }

  private def isFunction(symbol: Symbol): Boolean = {
    compiler.definitions.isFunctionSymbol(
      symbol.info.finalResultType.typeSymbol
    )
  }

  private def completionItemKind(r: Member): CompletionItemKind = {
    import org.eclipse.lsp4j.{CompletionItemKind => k}
    val symbol = r.sym
    val symbolIsFunction = isFunction(symbol)
    if (symbol.hasPackageFlag) k.Module
    else if (symbol.isPackageObject) k.Module
    else if (symbol.isModuleOrModuleClass) k.Module
    else if (symbol.isTraitOrInterface) k.Interface
    else if (symbol.isClass) k.Class
    else if (symbol.isMethod) k.Method
    else if (symbol.isCaseAccessor) k.Field
    else if (symbol.isVal && !symbolIsFunction) k.Value
    else if (symbol.isVar && !symbolIsFunction) k.Variable
    else if (symbol.isTypeParameterOrSkolem) k.TypeParameter
    else if (symbolIsFunction) k.Function
    else k.Value
  }

  /** Computes the relative relevance of a symbol in the completion list
   * This is an adaptation of
   * https://github.com/scala-ide/scala-ide/blob/a17ace0ee1be1875b8992664069d8ad26162eeee/org.scala-ide.sdt.core/src/org/scalaide/core/completion/ProposalRelevanceCalculator.scala
   */
  private def computeRelevance(
      sym: Symbol,
      viaView: Symbol,
      inherited: Boolean
  ): Int = {
    var relevance = 0
    // local symbols are more relevant
    if (sym.isLocalToBlock) relevance += 10
    // fields are more relevant than non fields
    if (sym.hasGetter) relevance += 5
    // non-inherited members are more relevant
    if (!inherited) relevance += 10
    // symbols not provided via an implicit are more relevant
    if (viaView == NoSymbol) relevance += 20
    if (!sym.hasPackageFlag) relevance += 30
    // accessors of case class members are more relevant
    if (sym.isCaseAccessor) relevance += 10
    // public symbols are more relevant
    if (sym.isPublic) relevance += 10
    // synthetic symbols are less relevant (e.g. `copy` on case classes)
    if (!sym.isSynthetic) relevance += 10
    // symbols whose owner is a base class are less relevant
    if (sym.owner != definitions.AnyClass && sym.owner != definitions.AnyRefClass && sym.owner != definitions.ObjectClass)
      relevance += 40
    relevance
  }

  private def safeCompletionsAt(
      position: Position
  ): (Option[Type], LookupKind, List[Member]) = {
    def expected(e: Throwable) = {
      println(s"Expected error '${e.getMessage}'")
      (None, LookupKind.None, Nil)
    }
    try {
      val completions = completionsAt(position)
      val items = filterInteresting(completions.matchingResults())
      val kind = completions match {
        case _: CompletionResult.ScopeMembers =>
          LookupKind.Scope
        case _: CompletionResult.TypeMembers =>
          LookupKind.Type
        case _ =>
          LookupKind.None
      }
      val qual = completions match {
        case t: CompletionResult.TypeMembers =>
          Option(t.qualifier.tpe)
        case _ =>
          None
      }
      (qual, kind, items)
    } catch {
      case e: CyclicReference
          if e.getMessage.contains("illegal cyclic reference") =>
        expected(e)
      case e: ScalaReflectionException
          if e.getMessage.contains("not a module") =>
        expected(e)
      case e: NullPointerException =>
        expected(e)
      case e: StringIndexOutOfBoundsException =>
        expected(e)
    }
  }

  implicit val byRelevance = new Ordering[Member] {
    val relevanceCache = new java.util.HashMap[Member, Int]
    def relevance(m: Member): Int = {
      relevanceCache.computeIfAbsent(
        m, {
          case TypeMember(sym, _, true, inherited, viaView) =>
            // scribe.debug(s"Relevance of ${sym.name}: ${computeRelevance(sym, viaView, inherited)}")
            -computeRelevance(sym, viaView, inherited)
          case ScopeMember(sym, _, true, _) =>
            -computeRelevance(sym, NoSymbol, inherited = false)
          case r =>
            0
        }
      )
    }
    override def compare(x: Member, y: Member): Int = {
      val byRelevance = Integer.compare(relevance(x), relevance(y))
      if (byRelevance != 0) byRelevance
      else IdentifierComparator.compare(x.sym.name, y.sym.name)
    }
  }

}
