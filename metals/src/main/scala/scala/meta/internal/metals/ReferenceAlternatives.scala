package scala.meta.internal.metals

import scala.meta.internal.semanticdb.TextDocument
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.mtags.DefinitionAlternatives.GlobalSymbol

case class ReferenceAlternatives(
    occ: String,
    references: Set[String]
)

object ReferenceAlternatives {
  // Returns alternatives symbols for which "goto definition" resolves to the occurrence symbol.
  def apply(
      doc: TextDocument,
      occ: String
  ): ReferenceAlternatives = {
    val desc = occ.desc
    val name = desc.name.value
    val isCopyOrApply = Set("apply", "copy")
    // Returns true if `info` is the companion object matching the occurrence class symbol.
    def isCompanionObject(info: SymbolInformation): Boolean =
      info.isObject &&
        info.displayName == name &&
        occ == Symbols.Global(
          info.symbol.owner,
          Descriptor.Type(info.displayName)
        )
    // Returns true if `info` is a parameter of a synthetic `copy` or `apply` matching the occurrence field symbol.
    def isCopyOrApplyParam(info: SymbolInformation): Boolean =
      info.isParameter &&
        info.displayName == name &&
        occ == (Symbol(info.symbol) match {
          case GlobalSymbol(
              GlobalSymbol(
                GlobalSymbol(owner, Descriptor.Term(obj)),
                Descriptor.Method("apply", _)
              ),
              _
              ) =>
            Symbols.Global(
              Symbols.Global(owner.value, Descriptor.Type(obj)),
              Descriptor.Term(name)
            )
          case GlobalSymbol(
              GlobalSymbol(
                GlobalSymbol(owner, Descriptor.Type(obj)),
                Descriptor.Method("copy", _)
              ),
              _
              ) =>
            Symbols.Global(
              Symbols.Global(owner.value, Descriptor.Type(obj)),
              Descriptor.Term(name)
            )
          case _ =>
            ""
        })
    // Returns true if `info` is companion var setter method for occ var getter.
    def isVarSetter(info: SymbolInformation): Boolean =
      info.displayName.endsWith("_=") &&
        info.displayName.startsWith(name) &&
        occ == (Symbol(info.symbol) match {
          case GlobalSymbol(owner, Descriptor.Method(setter, disambiguator)) =>
            Symbols.Global(
              owner.value,
              Descriptor.Method(setter.stripSuffix("_="), disambiguator)
            )
          case _ =>
            ""
        })
    val candidates = for {
      info <- doc.symbols.iterator
      if info.symbol != name
      if {
        isVarSetter(info) ||
        isCompanionObject(info) ||
        isCopyOrApplyParam(info)
      }
    } yield info.symbol
    val isCandidate = candidates.toSet
    val nonSyntheticSymbols = for {
      doc <- doc.occurrences
      if isCandidate(doc.symbol)
      if doc.role.isDefinition
    } yield doc.symbol
    ReferenceAlternatives(
      occ,
      isCandidate -- nonSyntheticSymbols + occ
    )
  }
}
