package scala.meta.internal.pantsbuild.commands

import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath
import scala.util.Try
import scala.concurrent.ExecutionContext
import scala.meta.internal.io.PathIO
import scala.meta.internal.metals.Timer
import scala.meta.internal.metals.Time
import scala.util.Failure
import scala.util.Success
import scala.meta.internal.pantsbuild.Export
import scala.meta.internal.pantsbuild.BloopPants
import scala.meta.internal.pantsbuild.VSCode
import scala.meta.internal.pantsbuild.MessageOnlyException
import scala.meta.internal.pantsbuild.IntelliJ

object SharedCommand {
  def interpretExport(export: Export): Int = {
    if (!export.pants.isFile) {
      scribe.error(
        s"No Pants build detected, file '${export.pants}' does not exist."
      )
      scribe.error(
        s"Is the working directory correct? (${PathIO.workingDirectory})"
      )
      1
    } else if (export.isRegenerate) {
      BloopPants.bloopRegenerate(
        AbsolutePath(export.workspace),
        export.targets
      )(ExecutionContext.global)
      0
    } else if (export.isVscode && export.isWorkspaceAndOutputSameDirectory) {
      VSCode.launch(export)
      0
    } else {
      val workspace = export.workspace
      val targets = export.targets
      val timer = new Timer(Time.system)
      val installResult =
        BloopPants.bloopInstall(export)(ExecutionContext.global)
      installResult match {
        case Failure(exception) =>
          exception match {
            case MessageOnlyException(message) =>
              scribe.error(message)
            case _ =>
              scribe.error(s"fastpass failed to run", exception)
          }
          1
        case Success(count) =>
          scribe.info(s"time: exported ${count} Pants target(s) in $timer")
          if (export.out != export.workspace) {
            scribe.info(s"output: ${export.out}")
            BloopPants.symlinkToOut(export)
          }
          if (export.isLaunchIntelliJ) {
            IntelliJ.launch(export.out, export.targets)
          } else if (export.isVscode) {
            VSCode.launch(export)
          }
          0
      }
    }
  }

  def noSuchProject(name: String): Int = {
    scribe.error(s"no such project: ${name}")
    1
  }

  def interpretRefresh(refresh: RefreshOptions): Int = { 1 }
}

case class ProjectRoot(
    root: AbsolutePath
) {
  val bspRoot: AbsolutePath = root.resolve(root.filename)
  val bspJson: AbsolutePath = bspRoot.resolve(".bsp").resolve("bloop.json")
}
case class Project(
    name: String,
    targets: List[String],
    root: ProjectRoot
)
object Project {
  def fromName(
      name: String,
      common: SharedOptions
  ): Option[Project] =
    fromCommon(common, _ == name).headOption
  def fromCommon(
      common: SharedOptions,
      isEnabled: String => Boolean = _ => true
  ): List[Project] = {
    for {
      project <- common.home.list.toBuffer[AbsolutePath].toList
      if (isEnabled(project.filename))
      root = ProjectRoot(project)
      if (root.bspJson.isFile)
      json <- Try(ujson.read(root.bspJson.readText)).toOption
      targets <- json.obj.get("pantsTargets")
    } yield Project(
      project.filename,
      targets.arr.map(_.str).toList,
      root
    )
  }
}
