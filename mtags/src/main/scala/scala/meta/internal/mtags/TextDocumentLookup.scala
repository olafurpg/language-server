package scala.meta.internal.mtags

import scala.meta.AbsolutePath
import scala.meta.internal.{semanticdb => s}

sealed abstract class TextDocumentLookup {
  case class MissingSemanticdb(file: AbsolutePath)
      extends Exception(s"missing SemanticDB: $file")
  case class StaleSemanticdb(file: AbsolutePath)
      extends Exception(s"stale SemanticDB: $file")
  final def isSuccess: Boolean =
    this.isInstanceOf[TextDocumentLookup.Success]
  final def toOption: Option[s.TextDocument] = this match {
    case TextDocumentLookup.Success(document) =>
      Some(document)
    case _ => None
  }
  final def get: s.TextDocument = this match {
    case TextDocumentLookup.Success(document) =>
      document
    case TextDocumentLookup.NotFound(file) =>
      throw MissingSemanticdb(file)
    case TextDocumentLookup.NoMatchingUri(file, _) =>
      throw MissingSemanticdb(file)
    case TextDocumentLookup.Stale(file, _, _) =>
      throw StaleSemanticdb(file)
  }
}
object TextDocumentLookup {
  def fromOption(
      path: AbsolutePath,
      doc: Option[s.TextDocument]
  ): TextDocumentLookup = doc match {
    case Some(value) => Success(value)
    case None => NotFound(path)
  }
  case class Success(document: s.TextDocument) extends TextDocumentLookup
  case class NotFound(file: AbsolutePath) extends TextDocumentLookup
  case class NoMatchingUri(file: AbsolutePath, documents: s.TextDocuments)
      extends TextDocumentLookup
  case class Stale(
      file: AbsolutePath,
      expectedMd5: String,
      document: s.TextDocument
  ) extends TextDocumentLookup
}
