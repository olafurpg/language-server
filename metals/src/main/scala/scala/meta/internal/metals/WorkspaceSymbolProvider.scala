package scala.meta.internal.metals

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.PriorityQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j
import org.eclipse.lsp4j.jsonrpc.CancelChecker
import org.eclipse.{lsp4j => l}
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.inputs.Input
import scala.meta.internal.classpath.ClasspathIndex
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.JavaMtags
import scala.meta.internal.mtags.ListFiles
import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.mtags.ScalaToplevelMtags
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.semanticdb.Language
import scala.meta.internal.semanticdb.Scala.Descriptor
import scala.meta.internal.semanticdb.Scala.Symbols
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.semanticdb.SymbolInformation.Kind
import scala.meta.internal.semanticdb.SymbolOccurrence
import scala.meta.internal.{semanticdb => s}
import scala.meta.io.AbsolutePath
import scala.meta.io.Classpath
import scala.meta.tokenizers.TokenizeException
import scala.util.control.NonFatal

final class WorkspaceSymbolProvider(
    workspace: AbsolutePath,
    statistics: StatisticsConfig,
    buildTargets: BuildTargets,
    index: OnDemandSymbolIndex
)(implicit ec: ExecutionContext) {
  private val files = new WorkspaceSources(workspace)
  private val inWorkspace = TrieMap.empty[Path, BloomFilter[CharSequence]]
  private var classpathIndex = ClasspathIndex(Classpath(Nil))
  private val inDependencies = TrieMap.empty[String, BloomFilter[CharSequence]]
  var maxResults = 20

  def search(query: String): Seq[l.SymbolInformation] = {
    search(query, () => ())
  }

  def search(query: String, token: CancelChecker): Seq[l.SymbolInformation] = {
    if (query.isEmpty) return Nil
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
    indexSourceDirectories()
    indexClasspath()
  }

  def didChange(path: AbsolutePath): Unit = {
    indexSource(path)
  }

  def didRemove(path: AbsolutePath): Unit = {
    inWorkspace.remove(path.toNIO)
  }

  def indexWorkspace(): Unit = {
    val timer = new Timer(Time.system)
    files.all.foreach(indexSource)
    if (statistics.isWorkspaceSymbol) {
      scribe.info(
        s"workspace-symbol: reindex ${inWorkspace.size} files in $timer"
      )
    }
    if (statistics.isMemory) {
      val footprint = Memory.footprint("workspace-symbol index", inWorkspace)
      scribe.info(s"memory: $footprint")
    }
  }
  private def indexClasspath(): Unit = {
    inDependencies.clear()
    val classpath = mutable.Set.empty[AbsolutePath]
    buildTargets.all.foreach { target =>
      classpath ++= target.scalac.classpath
    }
    classpathIndex =
      ClasspathIndex(Classpath(classpath.toList), includeJdk = true)
    val symtab = classpathIndex.dirs
    for {
      (pkg, element) <- symtab
    } {
      val buf = Fuzzy.bloomFilterSymbolStrings(element.members.keys)
      buf ++= Fuzzy.bloomFilterSymbolStrings(List(pkg), buf)
      val bloom = BloomFilters.create(buf.size)
      buf.foreach { key =>
        bloom.put(key)
      }
      inDependencies(pkg) = bloom
    }
  }
  private def indexSourceDirectories(): Unit = {
    for {
      dir <- buildTargets.sourceDirectories
      source <- ListFiles(dir)
      if source.isScalaOrJava && !inWorkspace.contains(source.toNIO)
    } {
      indexSource(source)
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
    foreachSymbol(input) {
      case SymbolDefinition(info, _, _) =>
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
    inWorkspace(source.toNIO) = bloom
    bloomFilterStrings.foreach { c =>
      bloom.put(c)
    }
  }

  case class SymbolDefinition(
      info: SymbolInformation,
      occ: SymbolOccurrence,
      owner: String
  ) {
    def toInfo(uri: String): l.SymbolInformation = {
      new l.SymbolInformation(
        info.displayName,
        info.kind.toLSP,
        new l.Location(uri, occ.range.get.toLSP),
        owner.replace('/', '.')
      )
    }
  }
  private def foreachSymbol(input: Input.VirtualFile)(
      fn: SymbolDefinition => Unit
  ): Unit = {
    input.toLanguage match {
      case Language.SCALA =>
        val mtags = new ScalaToplevelMtags(input, true) {
          override def visitOccurrence(
              occ: s.SymbolOccurrence,
              info: s.SymbolInformation,
              owner: String
          ): Unit = {
            fn(SymbolDefinition(info, occ, owner))
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
            fn(SymbolDefinition(info, occ, owner))
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
    val result = newPriorityQueue()
    val queries = WorkspaceSymbolQuery.fromQuery(query)
    def matches(info: s.SymbolInformation): Boolean = {
      WorkspaceSymbolProvider.isRelevantKind(info.kind) &&
      queries.exists(_.matches(info.symbol))
    }
    def searchWorkspaceSymbols(): Unit = {
      val timer = new Timer(Time.system)
      var visitsCount = 0
      var falsePositives = 0
      for {
        (path, bloom) <- inWorkspace
        _ = token.checkCanceled()
        if queries.exists(_.matches(bloom))
      } {
        visitsCount += 1
        var isFalsePositive = true
        val input = path.toUriInput
        foreachSymbol(input) { defn =>
          if (matches(defn.info)) {
            isFalsePositive = false
            result.add(defn.toInfo(input.path))
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
          s"workspace-symbol: query '${query}' returned ${result.size()} results in $timer from workspace, " +
            s"visited ${visitsCount} files (${falsePositiveRatio}% false positives)"
        )
      }
    }
    def searchDependencySymbols(): Unit = {
      val timer = new Timer(Time.system)
      case class Hit(pkg: String, name: String) {
        def toplevel: String = {
          val dollar = name.indexOf('$')
          val symname =
            if (dollar < 0) name.stripSuffix(".class")
            else name.substring(0, dollar)
          Symbols.Global(pkg, Descriptor.Type(symname))
        }
      }
      val buf = new PriorityQueue[Hit](
        (a, b) => Integer.compare(a.name.length, b.name.length)
      )
      for {
        (pkg, bloom) <- inDependencies
        if queries.exists(_.matches(bloom))
        (member, _) <- classpathIndex.dirs(pkg).members
        if member.endsWith(".class")
        name = member.subSequence(0, member.length - ".class".length)
        if name.charAt(name.length - 1) != '$'
        symbol = new ConcatSequence(pkg, name)
        isMatch = queries.exists(_.matches(symbol))
        if isMatch
      } {
        buf.add(Hit(pkg, member))
      }
      val classpathEntries = ArrayBuffer.empty[l.SymbolInformation]
      val isVisited = mutable.Set.empty[AbsolutePath]
      while (classpathEntries.length < maxResults && !buf.isEmpty) {
        val hit = buf.poll()
        for {
          defn <- index.definition(Symbol(hit.toplevel))
          if !isVisited(defn.path)
        } {
          isVisited += defn.path
          val input = defn.path.toInput
          lazy val uri = defn.path.toFileOnDisk(workspace).toURI.toString
          foreachSymbol(input) { defn =>
            if (matches(defn.info)) {
              classpathEntries += defn.toInfo(uri)
            }
          }
        }
      }
      classpathEntries.foreach { s =>
        result.add(s)
      }
      if (statistics.isWorkspaceSymbol) {
        scribe.info(
          s"workspace-symbol: query '${query}' returned ${buf.size()} results from classpath in $timer"
        )
      }
    }
//    searchWorkspaceSymbols()
    searchDependencySymbols()
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
