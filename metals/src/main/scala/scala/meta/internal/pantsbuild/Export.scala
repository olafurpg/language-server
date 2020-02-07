package scala.meta.internal.pantsbuild

import java.nio.file.Path
import scala.meta.internal.io.PathIO
import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.EmptyCancelToken
import scala.meta.pc.CancelToken

/**
 * The command-line argument parser for BloopPants.
 */
case class Export(
    isCache: Boolean = false,
    isRegenerate: Boolean = false,
    isIntelliJ: Boolean = false,
    isVscode: Boolean = false,
    isLaunchIntelliJ: Boolean = false,
    isSources: Boolean = true,
    isMergeTargetsInSameDirectory: Boolean = true,
    maxFileCount: Int = 5000,
    projectName: Option[String] = None,
    workspace: Path = PathIO.workingDirectory.toNIO,
    out: Path = PathIO.workingDirectory.toNIO,
    targets: List[String] = Nil,
    token: CancelToken = EmptyCancelToken,
    onFilemap: Filemap => Unit = _ => Unit
) {
  def pants: AbsolutePath = AbsolutePath(workspace.resolve("pants"))
  def isWorkspaceAndOutputSameDirectory: Boolean =
    workspace == out
}
