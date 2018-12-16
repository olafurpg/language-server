package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.ScalacOptionsResult
import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.{lsp4j => _}
import scala.collection.concurrent.TrieMap
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.SemanticdbClasspath
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.semanticdb.SymbolOccurrence
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.internal.semanticdb.TextDocuments
import scala.meta.io.AbsolutePath

final class ReferenceProvider(
    workspace: AbsolutePath,
    semanticdbs: Semanticdbs,
    buffers: Buffers,
    definition: DefinitionProvider
) {
  val index = TrieMap.empty[Path, BloomFilter[CharSequence]]
  def onScalacOptions(scalacOptions: ScalacOptionsResult): Unit = {
    for {
      item <- scalacOptions.getItems.asScala
    } {
      val targetroot = item.targetroot
      onChangeDirectory(targetroot.resolve(Directories.semanticdb).toNIO)
    }
  }
  def onDelete(file: Path): Unit = {
    index.remove(file)
  }
  def onChangeDirectory(dir: Path): Unit = {
    if (Files.isDirectory(dir)) {
      Files.walkFileTree(
        dir,
        new SimpleFileVisitor[Path] {
          override def visitFile(
              file: Path,
              attrs: BasicFileAttributes
          ): FileVisitResult = {
            onChange(file)
            super.visitFile(file, attrs)
          }
        }
      )
    }
  }
  def onChange(file: Path): Unit = {
    if (file.isSemanticdb) {
      val td = TextDocuments.parseFrom(Files.readAllBytes(file))
      val count = td.documents.foldLeft(0)(_ + _.occurrences.length)
      val bloom = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        Integer.valueOf(count * 2),
        0.01
      )
      index(file) = bloom
      for {
        d <- td.documents
        o <- d.occurrences
      } {
        bloom.put(o.symbol)
      }
    } else {
      scribe.warn(s"not semanticdb file: $file")
    }
  }

  def references(params: ReferenceParams): Seq[Location] = {
    val source = params.getTextDocument.getUri.toAbsolutePath
    semanticdbs.textDocument(source).documentIncludingStale match {
      case Some(doc) =>
        val Occurrence(distance, maybeOccurrence) =
          definition.positionOccurrence(source, params, doc)
        maybeOccurrence match {
          case Some(occurrence) =>
            references(source, params, doc, distance, occurrence)
          case None =>
            Nil
        }
      case None =>
        Nil
    }
  }

  private def references(
      source: AbsolutePath,
      params: ReferenceParams,
      snapshot: TextDocument,
      distance: TokenEditDistance,
      occ: SymbolOccurrence
  ): Seq[Location] = {
    if (occ.symbol.isLocal) {
      referenceLocations(
        snapshot,
        occ.symbol,
        distance,
        params.getTextDocument.getUri
      )
    } else {
      val results: Iterator[Location] = for {
        (path, bloom) <- index.iterator
        if bloom.mightContain(occ.symbol)
        scalaPath <- SemanticdbClasspath
          .toScala(workspace, AbsolutePath(path))
          .iterator
        semanticdb <- semanticdbs
          .textDocument(scalaPath)
          .documentIncludingStale
          .iterator
        semanticdbDistance = TokenEditDistance.fromBuffer(
          scalaPath,
          semanticdb,
          buffers
        )
        uri = scalaPath.toURI.toString
        reference <- referenceLocations(
          semanticdb,
          occ.symbol,
          semanticdbDistance,
          uri
        )
      } yield reference
      results.toSeq
    }
  }

  private def referenceLocations(
      snapshot: TextDocument,
      symbol: String,
      distance: TokenEditDistance,
      uri: String
  ): Seq[Location] =
    for {
      reference <- snapshot.occurrences
      if reference.symbol == symbol
      range <- reference.range.toList
      revised = distance.toRevised(range.startLine, range.startCharacter)
      dirtyLocation = reference.toLocation(uri)
      location <- revised.toLocation(dirtyLocation)
    } yield location
}
