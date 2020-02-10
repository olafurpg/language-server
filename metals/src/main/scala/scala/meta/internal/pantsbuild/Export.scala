package scala.meta.internal.pantsbuild

import java.nio.file.Path
import scala.meta.internal.io.PathIO
import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.EmptyCancelToken
import scala.meta.pc.CancelToken
import scala.meta.internal.pantsbuild.commands.OpenOptions
import scala.meta.internal.pantsbuild.commands.ProjectRoot
import scala.meta.internal.pantsbuild.commands.Project
import metaconfig.cli.CliApp

/**
 * The command-line argument parser for BloopPants.
 */
case class Export(
    project: Project,
    open: OpenOptions,
    app: CliApp,
    isCache: Boolean = false,
    isRegenerate: Boolean = false,
    isIntelliJ: Boolean = false,
    isVscode: Boolean = false,
    isLaunchIntelliJ: Boolean = false,
    isSources: Boolean = true,
    isMergeTargetsInSameDirectory: Boolean = true,
    maxFileCount: Int = 5000,
    token: CancelToken = EmptyCancelToken,
    onFilemap: Filemap => Unit = _ => Unit
) {
  def root = project.root
  def common = project.common
  def workspace = common.workspace
  def targets = project.targets
  def out = root.bspRoot.toNIO
  def pants: AbsolutePath = AbsolutePath(workspace.resolve("pants"))
}
