package scala.meta.internal.metals

import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.WorkspaceEdit
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.pc.CancelToken
import org.eclipse.lsp4j.TextDocumentPositionParams
import scala.meta.io.AbsolutePath
import scala.collection.mutable
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.internal.semanticdb.ClassSignature
import scala.meta.internal.semanticdb.TextDocuments
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.semanticdb.TypeRef

class RenameProvider(
    workspace: AbsolutePath,
    superclasses: Superclasses,
    definitionProvider: DefinitionProvider,
    semanticdbs: Semanticdbs,
    compilers: Compilers
) {

  def rename(params: RenameParams, token: CancelToken): WorkspaceEdit = {
    val path = params.getTextDocument().getUri().toAbsolutePath
    val definition = definitionProvider.definition(
      path,
      new TextDocumentPositionParams(
        params.getTextDocument(),
        params.getPosition()
      ),
      token
    )
    val sym = definition.symbol
    val doc = definition.semanticdb
      .orElse(
        semanticdbs.textDocument(path).documentIncludingStale
      )
      .getOrElse(TextDocument())
    val symtab = doc.symbols.iterator.map(i => i.symbol -> i).toMap
    val edit: Option[WorkspaceEdit] = for {
      info <- symtab.get(sym)
      if info.kind.isMethod && !info.isVal
    } yield {
      val methods = expandMethodSymbol(path, info, doc, symtab)
      pprint.log(methods)
      null
    }
    edit.orNull
  }

  implicit class XtensionSymbolInfo(info: SymbolInformation) {
    def parents: Seq[String] = {
      info.signature match {
        case c: ClassSignature =>
          c.parents.collect {
            case TypeRef(_, symbol, _) =>
              symbol
          }
        case _ => Nil
      }
    }
    def declarations: Seq[String] = {
      info.signature match {
        case c: ClassSignature =>
          c.declarations match {
            case None => Nil
            case Some(scope) => scope.symlinks
          }
        case _ => Nil
      }
    }
  }

  def expandMethodSymbol(
      path: AbsolutePath,
      method: SymbolInformation,
      doc: TextDocument,
      tab: Map[String, SymbolInformation]
  ): List[String] = {
    val sym = method.symbol
    val isVisited = mutable.Set.empty[String]
    val overrides = compilers.overrides(path, sym).toSet
    def isOverride(candidate: SymbolInformation): Boolean = {
      if (candidate.symbol.isLocal) {
        // NOTE(olafur) rename all overloads instead of only the specific override
        // because don't have the means yet to query override symbols for
        // anonymous classes, which would require implementing asSeenFrom for
        // SemanticDB symbols.
        candidate.displayName == method.displayName
      } else {
        overrides.contains(candidate.symbol)
      }
    }
    val owners =
      if (sym.isLocal) {
        for {
          info <- doc.symbols
          decl <- info.declarations
          if decl == sym
        } yield info.symbol
      } else {
        sym.owner :: Nil
      }
    val result = for {
      owner <- owners
      ownerInfo <- tab.get(owner).iterator
      parent <- ownerInfo.parents
      over <- parent :: compilers.parentSymbols(path, parent)
      subclass <- superclasses
        .allKnownSubclasses(over, isVisited)
        .iterator
      if subclass.semanticdbFile.isFile
      doc <- TextDocuments
        .parseFrom(subclass.semanticdbFile.readAllBytes)
        .documents
      if subclass.semanticdbFile.isFile
      symtab = doc.symbols.iterator.map(i => i.symbol -> i).toMap
      info <- symtab.get(subclass.symbol).iterator
      decl <- info.declarations
      declInfo <- symtab.get(decl)
      if isOverride(declInfo)
    } yield decl
    result.toList
  }
}
