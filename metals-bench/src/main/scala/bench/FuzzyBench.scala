package bench

import org.eclipse.lsp4j.SymbolInformation
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import scala.meta.internal.metals.StatisticsConfig
import scala.meta.internal.metals.WorkspaceSymbolProvider
import tests.TestingWorkspaceSymbolProvider

@State(Scope.Benchmark)
class BaseFuzzyBench {
  var symbols: WorkspaceSymbolProvider = _

  @Setup
  def setup(): Unit = {
    symbols = TestingWorkspaceSymbolProvider(
      AkkaSources.download(),
      statistics = StatisticsConfig.default
    )
    symbols.indexWorkspace()
  }
}

class FuzzyBench extends BaseFuzzyBench {

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
