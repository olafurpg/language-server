package scala.meta.internal.pc

import scala.meta.internal.metals.WorkspaceSymbolQuery
import scala.tools.nsc.symtab.Flags.{ACCESSOR, PARAMACCESSOR}

/**
 * Implementation for completions.
 *
 * Most of the code in this file is originally copied from the Scala presentation compiler,
 * but some parts have been adjusted to improve features and provide consistent experience
 * across Scala versions.
 */
trait Completions { self: PresentationCompiler =>
  final def metalsCompletionsAt(pos: Position): CompletionResult = {
    val focus1: Tree = typedTreeAt(pos)
    def typeCompletions(
        tree: Tree,
        qual: Tree,
        nameStart: Int,
        name: Name
    ): CompletionResult = {
      val qualPos = qual.pos
      val allTypeMembers = metalsTypeMembers(qualPos).toList.flatten
      val positionDelta: Int = pos.start - nameStart
      val subName: Name = name
        .newName(
          new String(pos.source.content, nameStart, pos.start - nameStart)
        )
        .encodedName
      CompletionResult.TypeMembers(
        positionDelta,
        qual,
        tree,
        allTypeMembers,
        subName
      )
    }
    pprint.log(focus1)
    focus1 match {
      case Import(i @ Ident(name), head :: Nil) if head.name == nme.ERROR =>
        val r = search.search(name.toString).toList
        pprint.log(r)
        val allMembers = metalsScopeMembers(pos)
        val nameStart = i.pos.start
        val positionDelta: Int = pos.start - nameStart
        val subName = name.subName(0, pos.start - i.pos.start)
        CompletionResult.ScopeMembers(positionDelta, allMembers, subName)
      case imp @ Import(qual, selectors) =>
        selectors.reverseIterator.find(_.namePos <= pos.start) match {
          case None => CompletionResult.NoResults
          case Some(selector) =>
            typeCompletions(imp, qual, selector.namePos, selector.name)
        }
      case sel @ Select(qual, name) =>
        val qualPos = qual.pos
        def fallback = qualPos.end + 2
        val source = pos.source
        val nameStart: Int = (qualPos.end + 1 until focus1.pos.end)
          .find(p => source.identifier(source.position(p)).exists(_.length > 0))
          .getOrElse(fallback)
        typeCompletions(sel, qual, nameStart, name)
      case Ident(name) =>
        val allMembers = metalsScopeMembers(pos)
        val positionDelta: Int = pos.start - focus1.pos.start
        val subName = name.subName(0, positionDelta)
        CompletionResult.ScopeMembers(positionDelta, allMembers, subName)
      case _ =>
        CompletionResult.NoResults
    }
  }

  private def metalsTypeMembers(pos: Position): Stream[List[TypeMember]] = {
    // Choosing which tree will tell us the type members at the given position:
    //   If pos leads to an Import, type the expr
    //   If pos leads to a Select, type the qualifier as long as it is not erroneous
    //     (this implies discarding the possibly incomplete name in the Select node)
    //   Otherwise, type the tree found at 'pos' directly.
    val tree0 = typedTreeAt(pos) match {
      case sel @ Select(qual, _) if sel.tpe == ErrorType => qual
      case Import(expr, _) => expr
      case t => t
    }
    val context = doLocateContext(pos)
    val shouldTypeQualifier = tree0.tpe match {
      case null => true
      case mt: MethodType => mt.isImplicit
      case pt: PolyType => isImplicitMethodType(pt.resultType)
      case _ => false
    }

    // TODO: guard with try/catch to deal with ill-typed qualifiers.
    val tree =
      if (shouldTypeQualifier) analyzer newTyper context typedQualifier tree0
      else tree0

    debugLog("typeMembers at " + tree + " " + tree.tpe)
    val superAccess = tree.isInstanceOf[Super]
    val members = new MetalsMembers[TypeMember]

    def addTypeMember(
        sym: Symbol,
        pre: Type,
        inherited: Boolean,
        viaView: Symbol
    ): Unit = {
      val implicitlyAdded = viaView != NoSymbol
      members.add(sym, pre, implicitlyAdded) { (s, st) =>
        val result = new TypeMember(
          s,
          st,
          context.isAccessible(
            if (s.hasGetter) s.getterIn(s.owner) else s,
            pre,
            superAccess && !implicitlyAdded
          ),
          inherited,
          viaView
        )
        result.prefix = pre
        result

      }
    }
    import analyzer.{SearchResult, ImplicitSearch}

    /** Create a function application of a given view function to `tree` and typechecked it.
     */
    def viewApply(view: SearchResult): Tree = {
      assert(view.tree != EmptyTree)
      val t = analyzer
        .newTyper(context.makeImplicit(reportAmbiguousErrors = false))
        .typed(Apply(view.tree, List(tree)) setPos tree.pos)
      if (!t.tpe.isErroneous) t
      else
        analyzer
          .newTyper(context.makeSilent(reportAmbiguousErrors = true))
          .typed(Apply(view.tree, List(tree)) setPos tree.pos)
          .onTypeError(EmptyTree)
    }

    val pre = stabilizedType(tree)

    val ownerTpe = tree.tpe match {
      case ImportType(expr) => expr.tpe
      case null => pre
      case MethodType(List(), rtpe) => rtpe
      case _ => tree.tpe
    }

    //print("add members")
    for (sym <- ownerTpe.members)
      addTypeMember(sym, pre, sym.owner != ownerTpe.typeSymbol, NoSymbol)
    members.allMembers #:: {
      //print("\nadd enrichment")
      val applicableViews: List[SearchResult] =
        if (ownerTpe.isErroneous) List()
        else
          new ImplicitSearch(
            tree,
            definitions.functionType(List(ownerTpe), definitions.AnyTpe),
            isView = true,
            context0 = context.makeImplicit(reportAmbiguousErrors = false)
          ).allImplicits
      for (view <- applicableViews) {
        val vtree = viewApply(view)
        val vpre = stabilizedType(vtree)
        for (sym <- vtree.tpe.members if sym.isTerm) {
          addTypeMember(sym, vpre, inherited = false, view.tree.symbol)
        }
      }
      //println()
      Stream(members.allMembers)
    }
  }
  private def metalsScopeMembers(pos: Position): List[ScopeMember] = {
    typedTreeAt(pos) // to make sure context is entered
    val context = doLocateContext(pos)
    val locals = new MetalsMembers[ScopeMember]
    val enclosing = new MetalsMembers[ScopeMember]
    def addScopeMember(sym: Symbol, pre: Type, viaImport: Tree): Unit =
      locals.add(sym, pre, implicitlyAdded = false) { (s, st) =>
        // imported val and var are always marked as inaccessible, but they could be accessed through their getters. scala/bug#7995
        val member =
          if (s.hasGetter)
            new ScopeMember(
              s,
              st,
              context.isAccessible(s.getter, pre, superAccess = false),
              viaImport
            )
          else
            new ScopeMember(
              s,
              st,
              context.isAccessible(s, pre, superAccess = false),
              viaImport
            )
        member.prefix = pre
        member
      }
    def localsToEnclosing(): Unit = {
      enclosing.addNonShadowed(locals)
      locals.clear()
    }
    //print("add scope members")
    var cx = context
    while (cx != NoContext) {
      for (sym <- cx.scope)
        addScopeMember(sym, NoPrefix, EmptyTree)
      localsToEnclosing()
      if (cx == cx.enclClass) {
        val pre = cx.prefix
        for (sym <- pre.members)
          addScopeMember(sym, pre, EmptyTree)
        localsToEnclosing()
      }
      cx = cx.outer
    }
    //print("\nadd imported members")
    for (imp <- context.imports) {
      val pre = imp.qual.tpe
      for (sym <- imp.allImportedSymbols)
        addScopeMember(sym, pre, imp.qual)
      localsToEnclosing()
    }
    // println()
    val result = enclosing.allMembers
    //    if (debugIDE) for (m <- result) println(m)
    result
  }

  private class MetalsMembers[M <: Member]
      extends scala.collection.mutable.LinkedHashMap[Name, Set[M]] {
    override def default(key: Name): Set[M] = Set()

    private def matching(sym: Symbol, symtpe: Type, ms: Set[M]): Option[M] =
      ms.find { m =>
        (m.sym.name == sym.name) && (m.sym.isType || (m.tpe matches symtpe))
      }

    private def keepSecond(
        m: M,
        sym: Symbol,
        implicitlyAdded: Boolean
    ): Boolean =
      m.sym.hasFlag(ACCESSOR | PARAMACCESSOR) &&
        !sym.hasFlag(ACCESSOR | PARAMACCESSOR) &&
        (!implicitlyAdded || m.implicitlyAdded)

    def add(sym: Symbol, pre: Type, implicitlyAdded: Boolean)(
        toMember: (Symbol, Type) => M
    ) {
      if ((sym.isGetter || sym.isSetter) && sym.accessed != NoSymbol) {
        add(sym.accessed, pre, implicitlyAdded)(toMember)
      } else if (!sym.name.decodedName.containsName("$") && !sym.isError && !sym.isArtifact && sym.hasRawInfo) {
        val symtpe = pre.memberType(sym) onTypeError ErrorType
        matching(sym, symtpe, this(sym.name)) match {
          case Some(m) =>
            if (keepSecond(m, sym, implicitlyAdded)) {
              //print(" -+ "+sym.name)
              this(sym.name) = this(sym.name) - m + toMember(sym, symtpe)
            }
          case None =>
            //print(" + "+sym.name)
            this(sym.name) = this(sym.name) + toMember(sym, symtpe)
        }
      }
    }

    def addNonShadowed(other: MetalsMembers[M]): Unit = {
      for ((name, ms) <- other)
        if (ms.nonEmpty && this(name).isEmpty) this(name) = ms
    }

    def allMembers: List[M] = values.toList.flatten
  }
}
