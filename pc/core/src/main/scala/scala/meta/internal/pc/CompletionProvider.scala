package scala.meta.internal.pc

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import scala.collection.mutable
import scala.meta.pc.CompletionItems
import scala.meta.pc.CompletionItems.LookupKind
import scala.tools.nsc.interactive.Global

class CompletionProvider(compiler: Global) {
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
    val isUsedLabel = mutable.Set.empty[String]
    val buf = new java.util.ArrayList[CompletionItem]()
    val (kind, results) = safeCompletionsAt(position)
    val items = new CompletionItems(kind)
    results
      .sortBy {
        case TypeMember(sym, _, true, inherited, viaView) =>
          // scribe.debug(s"Relevance of ${sym.name}: ${computeRelevance(sym, viaView, inherited)}")
          (-computeRelevance(sym, viaView, inherited), sym.nameString)
        case ScopeMember(sym, _, true, _) =>
          (-computeRelevance(sym, NoSymbol, inherited = false), sym.nameString)
        case r => (0, r.sym.nameString)
      }
      .iterator
      .zipWithIndex
      .foreach {
        case (r, idx) =>
          val label = r.symNameDropLocal.decoded
          if (!isUsedLabel(label)) {
            isUsedLabel += label
            val item = new CompletionItem(label)
            item.setPreselect(true)
            item.setDetail(r.sym.signatureString)
            item.setKind(completionItemKind(r))
            item.setSortText(f"${idx}%05d")
            buf.add(item)
          }
      }
    items.setItems(buf)
    items
  }

  private def isFunction(symbol: Symbol): Boolean = {
    compiler.definitions.isFunctionSymbol(
      symbol.info.finalResultType.typeSymbol
    )
  }

  private def completionItemKind(r: CompletionResult#M): CompletionItemKind = {
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
  ): (LookupKind, List[CompletionResult#M]) = {
    def expected(e: Throwable) = {
      println(s"Expected error '${e.getMessage}'")
      (LookupKind.None, Nil)
    }
    try {
      val completions = completionsAt(position)
      val items = completions.matchingResults().distinct
      val kind = completions match {
        case _: CompletionResult.ScopeMembers => LookupKind.Scope
        case _: CompletionResult.TypeMembers => LookupKind.Type
        case _ => LookupKind.None
      }
      (kind, items)
    } catch {
      case e: CyclicReference
          if e.getMessage.contains("illegal cyclic reference") =>
        // A quick google search reveals this happens regularly and there is
        // no general fix for it.
        expected(e)
      case e: ScalaReflectionException
          if e.getMessage.contains("not a module") =>
        // Do nothing, seems to happen regularly
        // scala.ScalaReflectionException: value <error: object a> is not a module
        // at scala.reflect.api.Symbols$SymbolApi.asModule(Symbols.scala:250)
        // at scala.reflect.api.Symbols$SymbolApi.asModule$(Symbols.scala:250)
        expected(e)
      case e: NullPointerException =>
        // do nothing, seems to happen regularly
        // java.lang.NullPointerException: null
        //   at scala.tools.nsc.Global$GlobalPhase.cancelled(Global.scala:408)
        //   at scala.tools.nsc.Global$GlobalPhase.applyPhase(Global.scala:418)
        //   at scala.tools.nsc.Global$Run.$anonfun$compileLate$3(Global.scala:1572)
        expected(e)
      case e: StringIndexOutOfBoundsException =>
        // NOTE(olafur) Let's log this for now while we are still learning more
        // about the PC. However, I haven't been able to avoid this exception
        // in some cases so I suspect it's here to stay until we fix it upstream.
        expected(e)
    }
  }
}
