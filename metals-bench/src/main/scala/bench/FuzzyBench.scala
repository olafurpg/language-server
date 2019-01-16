package bench

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.ScalacOptionsItem
import ch.epfl.scala.bsp4j.ScalacOptionsResult
import com.geirsson.coursiersmall.CoursierSmall
import com.geirsson.coursiersmall.Dependency
import com.geirsson.coursiersmall.Settings
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import scala.meta.internal.metals.MetalsEnrichments._
import org.eclipse.lsp4j.SymbolInformation
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.RecursivelyDelete
import scala.meta.internal.metals.StatisticsConfig
import scala.meta.internal.metals.WorkspaceSymbolProvider
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.io.AbsolutePath
import tests.Libraries
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
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def upper(): Seq[SymbolInformation] = {
    symbols.search(query)
  }

}

@State(Scope.Benchmark)
class ClasspathFuzzBench {
  var buildTargets = new BuildTargets()
  var symbols: WorkspaceSymbolProvider = _
  var tmp: AbsolutePath = _

  @Setup
  def setup(): Unit = {
    tmp = AbsolutePath(Files.createTempDirectory("metals"))
    val index = OnDemandSymbolIndex()
    symbols = TestingWorkspaceSymbolProvider(
      tmp,
      buildTargets = buildTargets,
      statistics = StatisticsConfig.default,
      index = index
    )
    val sources = Libraries.suite.flatMap(_.sources().entries).distinct
    val classpath = Libraries.suite.flatMap(_.classpath().entries).distinct
    sources.foreach(s => index.addSourceJar(s))
    val item = new ScalacOptionsItem(
      new BuildTargetIdentifier(""),
      Nil.asJava,
      classpath.map(_.toURI.toString).asJava,
      ""
    )
    buildTargets.addScalacOptions(new ScalacOptionsResult(List(item).asJava))
    symbols.onBuildTargetsUpdate()
  }

  @TearDown
  def teardown(): Unit = {
    RecursivelyDelete(tmp)
  }

  @Param(Array("InputStream", "Str", "Like", "Paths"))
  var query: String = _

  @Param(Array("5", "10", "20"))
  var maxResults: Int = _

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def run(): Seq[SymbolInformation] = {
    symbols.maxResults = maxResults
    symbols.search(query)
  }

}
