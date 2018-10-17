package scala.meta.internal.mtags
import scala.meta.io.AbsolutePath

trait TextDocuments {
  def textDocument(path: AbsolutePath): TextDocumentLookup
}
