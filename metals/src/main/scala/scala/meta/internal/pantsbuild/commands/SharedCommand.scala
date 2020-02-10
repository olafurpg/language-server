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
          IntelliJ.writeBsp(export.project)
          scribe.info(s"time: exported ${count} Pants target(s) in $timer")
          scribe.info(s"output: ${export.root.bspRoot}")
          BloopPants.symlinkToOut(export)
          OpenCommand.run(export.open, export.app)
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
  val bloopRoot: AbsolutePath = bspRoot.resolve(".bloop")
}

case class Project(
    common: SharedOptions,
    name: String,
    targets: List[String],
    root: ProjectRoot
) {
  def parentRoot: AbsolutePath = root.root
  def bspRoot: AbsolutePath = root.bspRoot
}
object Project {
  def create(
      name: String,
      common: SharedOptions,
      targets: List[String]
  ): Project = {
    Project(common, name, targets, ProjectRoot(common.home.resolve(name)))
  }
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
      common,
      project.filename,
      targets.arr.map(_.str).toList,
      root
    )
  }
}
