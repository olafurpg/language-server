package bench

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import scala.meta.internal.metals.ClasspathSearch
import scala.meta.internal.pc.ScalaPC
import scala.meta.io.AbsolutePath
import scala.meta.pc.CompletionItems
import scala.meta.pc.PC
import tests.Library
import tests.SimpleJavaSymbolIndexer

abstract class BaseCompletionBench {
  var libraries: List[Library] = Nil
  def downloadLibraries(): Unit = {
    libraries = Library.jdk :: Library.all
  }

  def classpath: List[Path] =
    libraries.flatMap(_.classpath.entries.map(_.toNIO))
  def sources: List[AbsolutePath] = libraries.flatMap(_.sources.entries)

  def newSearch(): ClasspathSearch = {
    require(libraries.nonEmpty)
    ClasspathSearch.fromClasspath(classpath, _ => 0)
  }

  def newIndexer() = new SimpleJavaSymbolIndexer(sources)

  def newPC(
      search: ClasspathSearch = newSearch(),
      indexer: SimpleJavaSymbolIndexer = newIndexer()
  ): ScalaPC = {
    new ScalaPC(classpath, Nil, indexer, search)
  }

  def scopeComplete(pc: PC): CompletionItems = {
    val code = "import Java\n"
    pc.complete("A.scala", code, code.length - 2)
  }
}

@State(Scope.Benchmark)
class OnDemandCompletionBench extends BaseCompletionBench {

  @Setup
  def setup(): Unit = {
    downloadLibraries()
  }

  @TearDown
  def teardown(): Unit = {}

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def run(): CompletionItems = {
    scopeComplete(newPC(newSearch(), newIndexer()))
  }
}

@State(Scope.Benchmark)
class CachedSearchAndCompilerCompletionBench extends BaseCompletionBench {
  var pc: PC = _

  @Setup
  def setup(): Unit = {
    downloadLibraries()
    pc = newPC()
  }

  @TearDown
  def teardown(): Unit = {}

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def run(): CompletionItems = {
    scopeComplete(pc)
  }
}

@State(Scope.Benchmark)
class CachedSearchCompletionBench extends BaseCompletionBench {
  var pc: PC = _
  var cachedSearch: ClasspathSearch = _

  @Setup
  def setup(): Unit = {
    downloadLibraries()
    cachedSearch = newSearch()
  }

  @TearDown
  def teardown(): Unit = {}

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def run(): CompletionItems = {
    scopeComplete(newPC(cachedSearch))
  }
}
