package bench

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.ScalacOptionsItem
import ch.epfl.scala.bsp4j.ScalacOptionsResult
import com.geirsson.coursiersmall.CoursierSmall
import com.geirsson.coursiersmall.Dependency
import com.geirsson.coursiersmall.Settings
import java.nio.file.Files
import scala.meta.internal.metals.MetalsEnrichments._
import org.eclipse.lsp4j.SymbolInformation
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.StatisticsConfig
import scala.meta.internal.metals.WorkspaceSymbolProvider
import scala.meta.io.AbsolutePath
import tests.TestingWorkspaceSymbolProvider

@State(Scope.Benchmark)
class WorkspaceFuzzBench {
  var symbols: WorkspaceSymbolProvider = _

  @Setup
  def setup(): Unit = {
    symbols = TestingWorkspaceSymbolProvider(
      AkkaSources.download(),
      statistics = StatisticsConfig.default
    )
    symbols.indexWorkspace()
  }

  @Param(
    Array("FSM", "Actor", "Actor(", "FSMFB", "ActRef", "actorref", "actorrefs",
      "fsmbuilder", "fsmfunctionbuilder", "abcdefghijklmnopqrstabcdefghijkl")
  )
  var query: String = _

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  def upper(): Seq[SymbolInformation] = {
    symbols.search(query)
  }

}

@State(Scope.Benchmark)
class ClasspathFuzzBench {
  var buildTargets = new BuildTargets()
  var symbols: WorkspaceSymbolProvider = _

  @Setup
  def setup(): Unit = {
    val tmp = Files.createTempDirectory("metals")
    symbols = TestingWorkspaceSymbolProvider(
      AbsolutePath(tmp),
      buildTargets = buildTargets,
      statistics = StatisticsConfig.default
    )
    val classpath = CoursierSmall.fetch(
      new Settings().withDependencies(
        List(
          new Dependency(
            "org.apache.spark",
            "spark-sql_2.12",
            "2.4.0"
          )
        )
      )
    )
    val item = new ScalacOptionsItem(
      new BuildTargetIdentifier(""),
      Nil.asJava,
      classpath.map(_.toUri.toString).asJava,
      ""
    )
    buildTargets.addScalacOptions(new ScalacOptionsResult(List(item).asJava))
    symbols.onBuildTargetsUpdate()
  }

  @Param(Array("Str", "Failure"))
  var query: String = _

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  def upper(): Seq[SymbolInformation] = {
    symbols.search(query)
  }

}
