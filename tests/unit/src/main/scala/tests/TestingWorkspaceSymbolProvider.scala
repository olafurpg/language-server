package tests

import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.StatisticsConfig
import scala.meta.internal.metals.WorkspaceSymbolProvider
import scala.meta.io.AbsolutePath
import scala.concurrent.ExecutionContext.Implicits.global
import scala.meta.internal.mtags.OnDemandSymbolIndex

object TestingWorkspaceSymbolProvider {
  def apply(
      workspace: AbsolutePath,
      buildTargets: BuildTargets = new BuildTargets,
      statistics: StatisticsConfig = StatisticsConfig.all,
      index: OnDemandSymbolIndex = OnDemandSymbolIndex()
  ): WorkspaceSymbolProvider = {
    new WorkspaceSymbolProvider(
      workspace = workspace,
      statistics = statistics,
      buildTargets = new BuildTargets,
      index = index
    )
  }
}
