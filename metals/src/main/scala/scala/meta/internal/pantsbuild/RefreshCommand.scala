package scala.meta.internal.pantsbuild

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import org.typelevel.paiges.Doc
import metaconfig.cli.Messages

object RefreshCommand extends Command[RefreshOptions]("refresh") {
  override def description: Doc = Doc.paragraph("Refresh an existing project")
  override def options: Doc = Messages.options(RefreshOptions())
  def run(refresh: RefreshOptions, app: CliApp): Int = {
    if (refresh.names.isEmpty) {
      scribe.error("no projects to refresh")
      1
    } else {
      val errors: List[Int] = refresh.names.map { name =>
        Project.fromName(name, refresh.common) match {
          case Some(project) =>
            SharedCommand.interpretExport(
              Export().copy(
                workspace = refresh.common.workspace,
                isCache = refresh.update,
                projectName = Some(name),
                targets = project.targets,
                out = project.root.bspRoot.toNIO
              )
            )
          case None =>
            SharedCommand.noSuchProject(name)
        }
      }
      errors.sum
    }
  }
}
