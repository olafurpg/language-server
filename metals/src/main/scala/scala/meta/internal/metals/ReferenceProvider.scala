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
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.DefinitionAlternatives.GlobalSymbol
import scala.meta.internal.mtags.SemanticdbClasspath
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.semanticdb.SymbolOccurrence
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.internal.semanticdb.TextDocuments
import scala.meta.internal.{semanticdb => s}
import scala.meta.io.AbsolutePath
import scala.util.control.NonFatal

final class ReferenceProvider(
    workspace: AbsolutePath,
    semanticdbs: Semanticdbs,
    buffers: Buffers,
    definition: DefinitionProvider,
    superclasses: Superclasses,
    compilers: () => Compilers
) {
  var referencedPackages = BloomFilters.create(10000)
  val index = TrieMap.empty[Path, BloomFilter[CharSequence]]

  def references(params: ReferenceParams): ReferencesResult = {
    val source = params.getTextDocument.getUri.toAbsolutePath
    index.get(source.toNIO)
    semanticdbs.textDocument(source).documentIncludingStale match {
      case Some(doc) =>
        val ResolvedSymbolOccurrence(distance, maybeOccurrence) =
          definition.positionOccurrence(source, params, doc)
        maybeOccurrence match {
          case Some(occurrence) =>
            val alternatives = referenceAlternatives(doc, occurrence.symbol)
            val locations = references(
              source,
              params,
              doc,
              distance,
              occurrence,
              alternatives,
              params.getContext.isIncludeDeclaration
            )
            ReferencesResult(
              occurrence.symbol,
              locations,
              Some(source),
              Some(doc)
            )
          case None =>
            ReferencesResult.empty
        }
      case None =>
        ReferencesResult.empty
    }
  }

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

  /**
   * Handle EventType.OVERFLOW, meaning we lost file events for a given path.
   *
   * We walk up the file tree to the parent `META-INF/semanticdb` parent directory
   * and re-index all of its `*.semanticdb` children.
   */
  def onOverflow(path: Path): Unit = {
    path.semanticdbRoot match {
      case Some(root) =>
        onChangeDirectory(root)
      case None =>
    }
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
      val syntheticsCount = td.documents.foldLeft(0)(_ + _.synthetics.length)
      val bloom = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        Integer.valueOf((count + syntheticsCount) * 2),
        0.01
      )
      index(file) = bloom
      td.documents.foreach { d =>
        d.occurrences.foreach { o =>
          if (o.symbol.endsWith("/")) {
            referencedPackages.put(o.symbol)
          }
          bloom.put(o.symbol)
        }
        d.synthetics.foreach { synthetic =>
          Synthetics.foreachSymbol(synthetic) { sym =>
            bloom.put(sym)
            Synthetics.Continue
          }
        }
        d.symbols.foreach(info => superclasses.visitSymbol(file, info))
      }
      resizeReferencedPackages()
    } else {
      scribe.warn(s"not semanticdb file: $file")
    }
  }

  def expandSymbol(
      path: AbsolutePath,
      symbol: String,
      doc: TextDocument
  ): List[String] = {
    val symtab = doc.symtab
    val displayName =
      if (symbol.isLocal)
        symtab.get(symbol) match {
          case Some(info) => info.displayName
          case None => symbol
        } else symbol.desc.name.value
    val isMethod = symbol.desc.isMethod ||
      symtab
        .get(symbol)
        .exists(info => info.symbol == symbol == info.kind.isMethod)
    if (isMethod) expandMethodSymbol(path, symbol, displayName, doc, symtab)
    else Nil
  }

  // Returns alternatives symbols for which "goto definition" resolves to the occurrence symbol.
  private def referenceAlternatives(
      doc: TextDocument,
      occ: String
  ): Set[String] = {
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
    // Returns true if `info` is a synthetic `copy` or `apply` of the occurrence class symbol.
    def isCopyOrApplyMethod(info: SymbolInformation): Boolean =
      info.isMethod &&
        isCopyOrApply(info.displayName) &&
        occ == (Symbol(info.symbol) match {
          case GlobalSymbol(
              GlobalSymbol(owner, Descriptor.Term(obj)),
              Descriptor.Method("apply", _)
              ) =>
            Symbols.Global(owner.value, Descriptor.Type(obj))
          case GlobalSymbol(
              GlobalSymbol(owner, Descriptor.Type(obj)),
              Descriptor.Method("copy", _)
              ) =>
            Symbols.Global(owner.value, Descriptor.Type(obj))
          case _ =>
            ""
        })
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
        isCopyOrApplyParam(info) ||
        isCopyOrApplyMethod(info)
      }
    } yield info.symbol
    val isCandidate = candidates.toSet
    val nonSyntheticSymbols = for {
      doc <- doc.occurrences
      if isCandidate(doc.symbol)
      if doc.role.isDefinition
    } yield doc.symbol
    isCandidate -- nonSyntheticSymbols
  }

  private def references(
      source: AbsolutePath,
      params: ReferenceParams,
      snapshot: TextDocument,
      distance: TokenEditDistance,
      occ: SymbolOccurrence,
      alternatives: Set[String],
      isIncludeDeclaration: Boolean
  ): Seq[Location] = {
    val isSymbol = alternatives + occ.symbol
    if (occ.symbol.isLocal) {
      referenceLocations(
        snapshot,
        isSymbol,
        distance,
        params.getTextDocument.getUri,
        isIncludeDeclaration
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
          semanticdb.text,
          buffers
        )
        uri = scalaPath.toURI.toString
        reference <- try {
          referenceLocations(
            semanticdb,
            isSymbol,
            semanticdbDistance,
            uri,
            isIncludeDeclaration
          )
        } catch {
          case NonFatal(e) =>
            // Can happen for example if the SemanticDB text is empty for some reason.
            scribe.error(s"reference: $scalaPath", e)
            Nil
        }
      } yield reference
      results.toSeq
    }
  }

  private def referenceLocations(
      snapshot: TextDocument,
      isSymbol: Set[String],
      distance: TokenEditDistance,
      uri: String,
      isIncludeDeclaration: Boolean
  ): Seq[Location] = {
    val buf = Seq.newBuilder[Location]
    def add(range: s.Range): Unit = {
      val revised = distance.toRevised(range.startLine, range.startCharacter)
      val dirtyLocation = range.toLocation(uri)
      for {
        location <- revised.toLocation(dirtyLocation)
      } {
        buf += location
      }
    }

    for {
      reference <- snapshot.occurrences
      if isSymbol(reference.symbol)
      if !reference.role.isDefinition || isIncludeDeclaration
      range <- reference.range.toList
    } add(range)

    for {
      synthetic <- snapshot.synthetics
      if Synthetics.existsSymbol(synthetic)(isSymbol)
      range <- synthetic.range.toList
    } add(range)

    buf.result()
  }

  private def resizeReferencedPackages(): Unit = {
    // Increase the size of the set of referenced packages if the false positive ratio is too high.
    if (referencedPackages.expectedFpp() > 0.05) {
      referencedPackages =
        BloomFilters.create(referencedPackages.approximateElementCount() * 2)
    }
  }

  private def expandMethodSymbol(
      path: AbsolutePath,
      sym: String,
      displayName: String,
      doc: TextDocument,
      tab: Map[String, SymbolInformation]
  ): List[String] = {
    val isVisited = mutable.Set.empty[String]
    val overrides = compilers().overrides(path, sym).toSet
    def isOverride(candidate: SymbolInformation): Boolean = {
      if (candidate.symbol.isLocal) {
        // NOTE(olafur) rename all overloads instead of only the specific override
        // because don't have the means yet to query override symbols for
        // anonymous classes, which would require implementing asSeenFrom for
        // SemanticDB symbols.
        candidate.displayName == displayName
      } else {
        overrides.contains(candidate.symbol)
      }
    }
    val owners =
      if (sym.isLocal) {
        for {
          info <- doc.symbols
          decl <- info.declarations
          if decl == sym
        } yield info.symbol
      } else {
        sym.owner :: Nil
      }
    val result = for {
      owner <- owners
      ownerInfo <- tab.get(owner).iterator
      parent <- ownerInfo.parents
      over <- parent :: compilers().parentSymbols(path, parent)
      subclass <- superclasses
        .allKnownSubclasses(over, isVisited)
        .iterator
      if subclass.semanticdbFile.isFile
      doc <- TextDocuments
        .parseFrom(subclass.semanticdbFile.readAllBytes)
        .documents
      if subclass.semanticdbFile.isFile
      symtab = doc.symbols.iterator.map(i => i.symbol -> i).toMap
      info <- symtab.get(subclass.symbol).iterator
      decl <- info.declarations
      declInfo <- symtab.get(decl)
      if isOverride(declInfo)
    } yield decl
    result.toList
  }

}
