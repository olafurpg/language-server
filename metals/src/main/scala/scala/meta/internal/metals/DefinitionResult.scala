package scala.meta.internal.metals

import java.util
import java.util.Collections
import org.eclipse.lsp4j.Location
import scala.meta.internal.semanticdb.Scala.Symbols
import scala.meta.io.AbsolutePath

case class DefinitionResult(
    locations: util.List[Location],
    symbol: String,
    definition: Option[AbsolutePath]
)

object DefinitionResult {
  def empty: DefinitionResult =
    DefinitionResult(Collections.emptyList(), Symbols.None, None)
}
