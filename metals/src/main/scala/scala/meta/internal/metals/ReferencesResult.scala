package scala.meta.internal.metals

import org.eclipse.lsp4j.Location
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.io.AbsolutePath

case class ReferencesResult(
    symbol: String,
    locations: Seq[Location],
    path: Option[AbsolutePath] = None,
    textDocument: Option[TextDocument] = None
)

object ReferencesResult {
  def empty: ReferencesResult = ReferencesResult("", Nil)
}
