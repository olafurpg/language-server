package scala.meta.internal.metals

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.io
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.PriorityQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j
import org.eclipse.lsp4j.jsonrpc.CancelChecker
import org.eclipse.{lsp4j => l}
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.inputs.Input
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.JavaMtags
import scala.meta.internal.mtags.ListFiles
import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.mtags.ScalaToplevelMtags
import scala.meta.internal.semanticdb.Language
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.semanticdb.SymbolInformation.Kind
import scala.meta.internal.semanticdb.SymbolOccurrence
import scala.meta.internal.{semanticdb => s}
import scala.meta.io.AbsolutePath
import scala.meta.tokenizers.TokenizeException
import scala.util.control.NonFatal

final class WorkspaceSymbolProvider(
    workspace: AbsolutePath,
    statistics: StatisticsConfig,
    buildTargets: BuildTargets
)(implicit ec: ExecutionContext) {
  private val files = new WorkspaceSources(workspace)
  private val index = TrieMap.empty[Path, BloomFilter[CharSequence]]

  def search(query: String): Seq[l.SymbolInformation] = {
    search(query, () => ())
  }

  def search(query: String, token: CancelChecker): Seq[l.SymbolInformation] = {
    try {
      searchUnsafe(query, token)
    } catch {
      case _: CancellationException =>
        Nil
    }
  }

  private val indexJob = new AtomicReference[Promise[Unit]]()
  def searchFuture(
      query: String,
      token: CancelChecker
  ): Future[Seq[l.SymbolInformation]] = {
    val promise = Promise[Unit]()
    if (indexJob.compareAndSet(null, promise)) {
      try indexWorkspace()
      finally promise.success(())
    }
    indexJob.get().future.map { _ =>
      // Wait until indexing is complete before returning results. The user can send
      // multiple parallel requests during initialization and we want to avoid the situation
      // where the results are returned from a partial index. Indexing should not take more than
      // 2-5 seconds (depending on hot/cold JVM) even for large repos like akka/akka.
      search(query, token)
    }
  }

  def onBuildTargetsUpdate(): Unit = {
    for {
      dir <- buildTargets.sourceDirectories
      source <- ListFiles(dir)
      if source.isScalaOrJava && !index.contains(source.toNIO)
    } {
      indexSource(source)
    }
  }

  def didChange(path: AbsolutePath): Unit = {
    indexSource(path)
  }

  def didRemove(path: AbsolutePath): Unit = {
    index.remove(path.toNIO)
  }

  def indexWorkspace(): Unit = {
    val timer = new Timer(Time.system)
    files.all.foreach(indexSource)
    if (statistics.isWorkspaceSymbol) {
      scribe.info(
        s"workspace-symbol: reindex ${index.size} files in $timer"
      )
    }
    if (statistics.isMemory) {
      val footprint = Memory.footprint("workspace-symbol index", index)
      scribe.info(s"memory: $footprint")
    }
  }

  private def indexSource(source: AbsolutePath): Unit = {
    val language = source.toLanguage
    if (language.isScala || language.isJava) {
      try {
        indexSourceUnsafe(source)
      } catch {
        case NonFatal(e) =>
          scribe.warn(source.toString(), e)
      }
    }
  }

  private def indexSourceUnsafe(source: AbsolutePath): Unit = {
    val input = source.toInput
    val symbols = ArrayBuffer.empty[String]
    foreachSymbol(input) { (info, _, _) =>
      if (WorkspaceSymbolProvider.isRelevantKind(info.kind)) {
        symbols += info.symbol
      }
    }
    val bloomFilterStrings = Fuzzy.bloomFilterSymbolStrings(symbols)
    val bloom = BloomFilter.create[CharSequence](
      Funnels.stringFunnel(StandardCharsets.UTF_8),
      Integer.valueOf(bloomFilterStrings.size),
      0.01
    )
    index(source.toNIO) = bloom
    bloomFilterStrings.foreach { c =>
      bloom.put(c)
    }
  }

  private def foreachSymbol(input: Input.VirtualFile)(
      fn: (SymbolInformation, SymbolOccurrence, String) => Unit
  ): Unit = {
    input.toLanguage match {
      case Language.SCALA =>
        val mtags = new ScalaToplevelMtags(input, true) {
          override def visitOccurrence(
              occ: s.SymbolOccurrence,
              info: s.SymbolInformation,
              owner: String
          ): Unit = {
            fn(info, occ, owner)
          }
        }
        try mtags.indexRoot()
        catch {
          case _: TokenizeException =>
            () // ignore because we don't need to index untokenizable files.
        }
      case Language.JAVA =>
        val mtags = new JavaMtags(input) {
          override def visitOccurrence(
              occ: s.SymbolOccurrence,
              info: s.SymbolInformation,
              owner: String
          ): Unit = {
            fn(info, occ, owner)
          }
        }
        try mtags.indexRoot()
        catch {
          case NonFatal(e) =>
        }
      case _ =>
    }
  }

  private def newPriorityQueue(): PriorityQueue[lsp4j.SymbolInformation] =
    new PriorityQueue[l.SymbolInformation](
      (o1, o2) => -Integer.compare(o1.getName.length, o2.getName.length)
    )

  private def searchUnsafe(
      query: String,
      token: CancelChecker
  ): Seq[l.SymbolInformation] = {
    if (query.isEmpty) return Nil
    val timer = new Timer(Time.system)
    val result = newPriorityQueue()
    val queries = WorkspaceSymbolQuery.fromQuery(query)
    var visitsCount = 0
    var falsePositives = 0
    def bloomMatches(bloom: BloomFilter[CharSequence]): Boolean = {
      queries.exists { q =>
        q.combinations.forall(bloom.mightContain)
      }
    }
    def symbolMatches(symbol: String): Boolean = {
      queries.exists { q =>
        Fuzzy.matches(q.query, symbol)
      }
    }
    for {
      (path, bloom) <- index
      _ = token.checkCanceled()
      if bloomMatches(bloom)
    } {
      visitsCount += 1
      var isFalsePositive = true
      val input = path.toUriInput
      foreachSymbol(input) { (info, occ, owner) =>
        if (WorkspaceSymbolProvider.isRelevantKind(info.kind) &&
          symbolMatches(info.symbol)) {
          isFalsePositive = false
          val linfo = new l.SymbolInformation(
            info.displayName,
            info.kind.toLSP,
            new l.Location(input.path, occ.range.get.toLSP),
            owner.replace('/', '.')
          )
          result.add(linfo)
        }
      }
      if (isFalsePositive) {
        falsePositives += 1
      }
    }
    if (statistics.isWorkspaceSymbol) {
      val falsePositiveRatio =
        ((falsePositives.toDouble / visitsCount) * 100).toInt
      scribe.info(
        s"workspace-symbol: query '${query}' returned ${result.size()} results in $timer, " +
          s"visited ${visitsCount} files (${falsePositiveRatio}% false positives)"
      )
    }
    result.asScala.toSeq.sortBy(_.getName.length)
  }
}

object WorkspaceSymbolProvider {
  def isRelevantKind(kind: Kind): Boolean = {
    kind match {
      case Kind.OBJECT | Kind.PACKAGE_OBJECT | Kind.CLASS | Kind.TRAIT |
          Kind.INTERFACE =>
        true
      case _ =>
        false
    }
  }
}
