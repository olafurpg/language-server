package scala.meta.internal.pantsbuild.commands

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import scala.meta.internal.pantsbuild.BloopPants
import org.typelevel.paiges.Doc
import metaconfig.cli.Messages

object LinkCommand extends Command[LinkOptions]("link") {
  override def options: Doc = Messages.options(LinkOptions())
  override def description: Doc =
    Doc.text("Symlink the Bloop build into the workspace directory")
  override def usage: Doc = Doc.text("fastpass link PROJECT_NAME")
  override def examples: Doc =
    Doc.intercalate(
      Doc.line,
      List(
        "fastpass link PROJECT_NAME",
        "# List all Bloop targets in the newly linked project",
        "bloop projects"
      ).map(Doc.text)
    )
  def run(link: LinkOptions, app: CliApp): Int = {
    link.projects match {
      case Nil =>
        app.error("no projects to link")
        1
      case name :: Nil =>
        Project.fromName(name, link.common) match {
          case Some(project) =>
            BloopPants.symlinkToOut(project)
            scribe.info(s"linked ${project.name}")
            0
          case None =>
            SharedCommand.noSuchProject(name)
        }
      case obtained =>
        app.error(
          s"can only link 1 project, obtained ${obtained.length} project '${obtained.mkString(" ")}'"
        )
    }
    0
  }
}
