package tests

import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.StatisticsConfig
import scala.meta.internal.metals.WorkspaceSymbolProvider
import scala.meta.io.AbsolutePath
import scala.concurrent.ExecutionContext.Implicits.global

object TestingWorkspaceSymbolProvider {
  def apply(
      workspace: AbsolutePath,
      buildTargets: BuildTargets = new BuildTargets,
      statistics: StatisticsConfig = StatisticsConfig.all
  ): WorkspaceSymbolProvider = {
    new WorkspaceSymbolProvider(
      workspace,
      statistics,
      new BuildTargets
    )
  }
}
