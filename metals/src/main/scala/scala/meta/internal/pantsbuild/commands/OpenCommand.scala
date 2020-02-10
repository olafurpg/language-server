package scala.meta.internal.pantsbuild.commands

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import scala.meta.internal.pantsbuild.IntelliJ
import org.typelevel.paiges.Doc
import metaconfig.cli.Messages
import scala.meta.internal.pantsbuild.VSCode

object OpenCommand extends Command[OpenOptions]("open") {
  override def description: Doc =
    Doc.paragraph("Launch IntelliJ or VS Code with the given project.")
  override def usage: Doc =
    Doc.paragraph("fastpass open [OPTIONS] [PROJECT_NAME ...]")
  override def options: Doc =
    Messages.options(OpenOptions())
  override def examples: Doc =
    Doc.text("fastpass open --intellij PROJECT_NAME")
  def run(open: OpenOptions, app: CliApp): Int = {
    SharedCommand.withOneProject(
      "open",
      open.projects,
      open.common,
      app
    ) { project =>
      if (open.intellij) {
        IntelliJ.launch(project)
      }
      if (open.vscode) {
        VSCode.launch(project)
      }
      0
    }
  }
}
