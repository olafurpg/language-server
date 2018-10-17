package scala.meta.internal.metals

import java.util.Collections
import org.eclipse.lsp4j.TextDocumentPositionParams
import scala.meta.inputs.Input
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.GlobalSymbolIndex
import scala.meta.internal.mtags.Mtags
import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.mtags.TextDocumentLookup
import scala.meta.internal.mtags.TextDocuments
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.io.AbsolutePath
import scala.meta.io.RelativePath

object DefinitionProvider {
  val DependencySource = RelativePath(".metals").resolve("readonly")

  def definition(
      path: AbsolutePath,
      position: TextDocumentPositionParams,
      workspace: AbsolutePath,
      mtags: Mtags,
      buffers: Buffers,
      index: GlobalSymbolIndex,
      textDocuments: TextDocuments,
      config: MetalsServerConfig
  )(implicit statusBar: StatusBar): DefinitionResult = {
    textDocuments.textDocument(path).toOption match {
      case None =>
        scribe.warn(s"no semanticdb: $path")
        statusBar.addMessage("$(alert) No SemanticDB")
        DefinitionResult.empty
      case Some(doc) =>
        val bufferInput = path.toInputFromBuffers(buffers)
        val docInput = Input.VirtualFile(bufferInput.path, doc.text)
        val editDistance = TokenEditDistance(docInput, bufferInput)
        val originalPosition = editDistance.toOriginal(
          position.getPosition.getLine,
          position.getPosition.getCharacter
        )
        val queryPosition0 = originalPosition.foldResult(
          onPosition = pos => {
            position.getPosition.setLine(pos.startLine)
            position.getPosition.setCharacter(pos.startColumn)
            Some(position.getPosition)
          },
          onUnchanged = () => Some(position.getPosition),
          onNoMatch = () => None
        )

        val occurrence = for {
          queryPosition <- queryPosition0
          occurrence <- doc.occurrences.find(_.encloses(queryPosition))
        } yield occurrence
        val result = occurrence.flatMap { occ =>
          val isLocal =
            occ.symbol.isLocal ||
              doc.occurrences.exists { localOccurrence =>
                localOccurrence.role.isDefinition &&
                localOccurrence.symbol == occ.symbol
              }
          // TODO(olafur): create case class!!
          val ddoc: Option[
            (
                TextDocument,
                TokenEditDistance,
                String,
                Option[AbsolutePath],
                String
            )
          ] =
            if (isLocal) {
              Some(
                (
                  doc,
                  editDistance,
                  occ.symbol,
                  None,
                  position.getTextDocument.getUri
                )
              )
            } else {
              for {
                defn <- index.definition(Symbol(occ.symbol))
                defnDoc = textDocuments.textDocument(defn.path) match {
                  case TextDocumentLookup.Success(d) => d
                  case TextDocumentLookup.Stale(_, _, d) => d
                  case _ =>
                    // read file from disk instead of buffers because text on disk is more
                    // likely to parse successfully.
                    val defnRevisedInput = defn.path.toInput
                    mtags.index(defn.path.toLanguage, defnRevisedInput)
                }
                defnPathInput = defn.path.toInputFromBuffers(buffers)
                defnOriginalInput = Input.VirtualFile(
                  defnPathInput.path,
                  defnDoc.text
                )
                destinationFile = defn.path.toFileOnDisk(workspace, config)
                defnEditDistance = TokenEditDistance(
                  defnOriginalInput,
                  defnPathInput,
                )
              } yield {
                if (defnEditDistance eq TokenEditDistance.noMatch) {
                  pprint.log(defnOriginalInput)
                  pprint.log(defnPathInput)
                }
                (
                  defnDoc,
                  defnEditDistance,
                  defn.definitionSymbol.value,
                  Some(destinationFile),
                  destinationFile.toURI.toString
                )
              }
            }
          for {
            (defnDoc, distance, symbol, path, uri) <- ddoc
            location <- defnDoc.definition(uri, symbol)
            revisedPosition = distance.toRevised(
              location.getRange.getStart.getLine,
              location.getRange.getStart.getCharacter
            )
            result <- revisedPosition.foldResult(
              pos => {
                val start = location.getRange.getStart
                start.setLine(pos.startLine)
                start.setCharacter(pos.startColumn)
                val end = location.getRange.getEnd
                end.setLine(pos.endLine)
                end.setCharacter(pos.endColumn)
                Some(location)
              },
              () => Some(location),
              () => None
            )
          } yield (result, symbol, path)
        }

        result match {
          case Some((location, symbol, definitionPath)) =>
            DefinitionResult(
              Collections.singletonList(location),
              symbol,
              definition = definitionPath
            )
          case None =>
            DefinitionResult(
              Collections.emptyList(),
              occurrence.fold("")(_.symbol),
              definition = None
            )
        }
    }
  }

}
