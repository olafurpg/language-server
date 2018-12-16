package scala.meta.internal.metals

import scala.meta.internal.semanticdb.SymbolOccurrence

case class Occurrence(
    distance: TokenEditDistance,
    occurrence: Option[SymbolOccurrence]
)
