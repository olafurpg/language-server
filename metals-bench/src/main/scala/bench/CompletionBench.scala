package bench

import java.nio.file.Files
import scala.collection.JavaConverters._
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import scala.meta.internal.metals.ClasspathSearch
import scala.meta.internal.metals.RecursivelyDelete
import scala.meta.internal.pc.LibraryManager
import scala.meta.internal.pc.ScalaPC
import scala.meta.io.AbsolutePath
import scala.meta.pc.CompletionItems
import scala.meta.pc.PC
import tests.Library
import tests.SimpleJavaSymbolIndexer

@State(Scope.Benchmark)
abstract class BaseCompletionBench {
  var libraries: List[Library] = Nil
  var completions: Map[String, SourceCompletion] = Map.empty
  var workspace: Path = _

  def runSetup(): Unit
  def runTeardown(): Unit = {}

  def presentationCompiler(): PC

  @Setup
  def setup(): Unit = {
    workspace = Files.createTempDirectory("metals")
    runSetup()
  }

  @TearDown
  def teardown(): Unit = {
    RecursivelyDelete(AbsolutePath(workspace))
    runTeardown()
  }

  def downloadLibraries(): Unit = {
    libraries = Library.jdk :: Library.all
    completions = Map(
      "scopeOpen" -> SourceCompletion(
        "A.scala",
        "import Java\n",
        "import Java".length
      ),
      "scopeDeep" -> SourceCompletion.fromPath(
        "UnzipWithApply.scala",
        "if (pendin@@g12) pendingCount -= 1"
      ),
      "memberDeep" -> SourceCompletion.fromPath(
        "UnzipWithApply.scala",
        "shape.@@out21"
      )
    )
  }
  @Param(Array("scopeOpen", "scopeDeep", "memberDeep"))
  var completion: String = _

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def complete(): CompletionItems = {
    val pc = presentationCompiler()
    val result = currentCompletion.complete(pc)
    val diagnostics = pc.diagnostics()
    require(diagnostics.isEmpty, diagnostics.asScala.mkString("\n", "\n", "\n"))
    result
  }

  def currentCompletion: SourceCompletion = completions(completion)

  def classpath: List[Path] =
    libraries.flatMap(_.classpath.entries.map(_.toNIO))
  def sources: List[AbsolutePath] = libraries.flatMap(_.sources.entries)

  def newSearch(): ClasspathSearch = {
    require(libraries.nonEmpty)
    ClasspathSearch.fromClasspath(classpath, _ => 0)
  }
  def newIndexer() = new SimpleJavaSymbolIndexer(sources)

  def newLibmanager(): LibraryManager = {
    val libmanager = LibraryManager(workspace)
    val _ = libmanager.buildFlatFileSystem(classpath) // pre-compute manager
    libmanager
  }

  def newPC(
      search: ClasspathSearch = newSearch(),
      indexer: SimpleJavaSymbolIndexer = newIndexer(),
      libmanager: Option[LibraryManager] = None
  ): ScalaPC = {
    new ScalaPC(classpath, Nil, indexer, search, libmanager)
  }

  def scopeComplete(pc: PC): CompletionItems = {
    val code = "import Java\n"
    pc.complete("A.scala", code, code.length - 2)
  }
}

class OnDemandCompletionBench extends BaseCompletionBench {
  override def runSetup(): Unit = downloadLibraries()
  override def presentationCompiler(): PC = newPC(newSearch(), newIndexer())
}

class CachedSearchAndCompilerCompletionBench extends BaseCompletionBench {
  var pc: PC = _

  override def runSetup(): Unit = {
    downloadLibraries()
    pc = newPC()
  }

  override def presentationCompiler(): PC = pc
}

class CachedSearchAndMemoryCompilerCompletionBench extends BaseCompletionBench {
  var pc: PC = _

  override def runSetup(): Unit = {
    downloadLibraries()
    pc = newPC(libmanager = Some(newLibmanager()))
  }

  override def presentationCompiler(): PC = pc
}

class CachedSearchCompletionBench extends BaseCompletionBench {
  var pc: PC = _
  var cachedSearch: ClasspathSearch = _

  override def runSetup(): Unit = {
    downloadLibraries()
    cachedSearch = newSearch()
  }

  override def presentationCompiler(): PC = newPC(cachedSearch)

}

class CachedSearchMemoryCompilerCompletionBench extends BaseCompletionBench {
  var pc: PC = _
  var cachedSearch: ClasspathSearch = _
  var libmanager: LibraryManager = _

  override def runSetup(): Unit = {
    downloadLibraries()
    libmanager = newLibmanager()
    cachedSearch = newSearch()
  }

  override def presentationCompiler(): PC =
    newPC(search = cachedSearch, libmanager = Some(libmanager))

}
