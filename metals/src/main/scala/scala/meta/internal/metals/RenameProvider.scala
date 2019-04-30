package scala.meta.internal.metals

import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.WorkspaceEdit
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.pc.CancelToken
import scala.meta.io.AbsolutePath
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.ReferenceContext

class RenameProvider(
    workspace: AbsolutePath,
    superclasses: Superclasses,
    referenceProvider: ReferenceProvider,
    semanticdbs: Semanticdbs,
    compilers: Compilers
) {

  def rename(params: RenameParams, token: CancelToken): WorkspaceEdit = {
    val referenceParams = new ReferenceParams(new ReferenceContext(true))
    referenceParams.setTextDocument(params.getTextDocument())
    referenceParams.setPosition(params.getPosition())
    val references = referenceProvider.references(referenceParams)
    val edits = for {
      doc <- references.textDocument
      path <- references.path
    } yield {
      references.locations
    }
    // val path = params.getTextDocument().getUri().toAbsolutePath
    // val definition = definitionProvider.definition(
    //   path,
    //   new TextDocumentPositionParams(
    //     params.getTextDocument(),
    //     params.getPosition()
    //   ),
    //   token
    // )
    // val sym = definition.symbol
    // val doc = definition.semanticdb
    //   .orElse(
    //     semanticdbs.textDocument(path).documentIncludingStale
    //   )
    //   .getOrElse(TextDocument())
    // val symtab = doc.symbols.iterator.map(i => i.symbol -> i).toMap
    // val edit: Option[WorkspaceEdit] = for {
    //   info <- symtab.get(sym)
    //   if info.kind.isMethod && !info.isVal
    // } yield {
    //   val methods = expandMethodSymbol(path, info, doc, symtab)
    //   pprint.log(methods)
    //   null
    // }
    pprint.log(edits)
    null
  }

}
