package scala.meta.internal.metals

import scala.meta.internal.mtags.TextDocumentLookup
import scala.meta.internal.mtags.TextDocuments
import scala.meta.io.AbsolutePath
import scala.util.control.NonFatal

final case class AggregateTextDocuments(underlying: List[TextDocuments])
    extends TextDocuments {
  override def textDocument(path: AbsolutePath): TextDocumentLookup = {
    def loop(xs: List[TextDocuments]): TextDocumentLookup = xs match {
      case Nil => TextDocumentLookup.NotFound(path)
      case head :: tail =>
        val result = head.textDocument(path)
        if (result.isSuccess) result
        else loop(tail)
    }
    try {
      loop(underlying)
    } catch {
      case NonFatal(e) =>
        scribe.error(s"text document: ${path.toURI}", e)
        TextDocumentLookup.NotFound(path)
    }
  }
}
