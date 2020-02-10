package scala.meta.internal.pantsbuild.commands

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import scala.meta.internal.pantsbuild.BloopPants
import org.typelevel.paiges.Doc
import metaconfig.cli.Messages
import fansi.Color

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
    SharedCommand.withOneProject(
      "link",
      link.projects,
      link.common,
      app
    ) { project =>
      BloopPants.symlinkToOut(project)
      app.out.println(s"${Color.LightBlue("info:")} linked ${project.name}")
      0
    }
  }
}
