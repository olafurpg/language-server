package scala.meta.internal.metals

import scala.meta.internal.{semanticdb => s}
import org.eclipse.{lsp4j => l}
import scala.meta.internal.semanticdb.SymbolInformation.{Kind => k}

object PCEnrichments extends PCEnrichments
trait PCEnrichments {
  implicit class XtensionRangeBuildProtocol(range: s.Range) {
    def toLocation(uri: String): l.Location = {
      new l.Location(uri, range.toLSP)
    }
    def toLSP: l.Range = {
      val start = new l.Position(range.startLine, range.startCharacter)
      val end = new l.Position(range.endLine, range.endCharacter)
      new l.Range(start, end)
    }
    def encloses(other: l.Position): Boolean = {
      range.startLine <= other.getLine &&
      range.endLine >= other.getLine &&
      range.startCharacter <= other.getCharacter &&
      range.endCharacter > other.getCharacter
    }
    def encloses(other: l.Range): Boolean = {
      encloses(other.getStart) &&
      encloses(other.getEnd)
    }
  }

  implicit class XtensionSymbolInformation(kind: s.SymbolInformation.Kind) {
    def toLSP: l.SymbolKind = kind match {
      case k.LOCAL => l.SymbolKind.Variable
      case k.FIELD => l.SymbolKind.Field
      case k.METHOD => l.SymbolKind.Method
      case k.CONSTRUCTOR => l.SymbolKind.Constructor
      case k.MACRO => l.SymbolKind.Method
      case k.TYPE => l.SymbolKind.Class
      case k.PARAMETER => l.SymbolKind.Variable
      case k.SELF_PARAMETER => l.SymbolKind.Variable
      case k.TYPE_PARAMETER => l.SymbolKind.TypeParameter
      case k.OBJECT => l.SymbolKind.Object
      case k.PACKAGE => l.SymbolKind.Module
      case k.PACKAGE_OBJECT => l.SymbolKind.Module
      case k.CLASS => l.SymbolKind.Class
      case k.TRAIT => l.SymbolKind.Interface
      case k.INTERFACE => l.SymbolKind.Interface
      case _ => l.SymbolKind.Class
    }
  }
}
