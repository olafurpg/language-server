package bench

import scala.collection.JavaConverters._
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import scala.meta.internal.metals.ClasspathSearch
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

  def runSetup(): Unit

  def presentationCompiler(): PC

  @Setup
  def setup(): Unit = {
    runSetup()
  }

  def downloadLibraries(): Unit = {
    libraries = Library.jdk :: Library.all
    completions = Map(
      "scopeOpen" -> SourceCompletion(
        "A.scala",
        "import Java\n",
        "import Jav".length
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
  @Param(Array("openScope", "extraLargeMember"))
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

class CachedSearchCompletionBench extends BaseCompletionBench {
  var pc: PC = _
  var cachedSearch: ClasspathSearch = _

  override def runSetup(): Unit = {
    downloadLibraries()
    cachedSearch = newSearch()
  }

  override def presentationCompiler(): PC = newPC(cachedSearch)

}