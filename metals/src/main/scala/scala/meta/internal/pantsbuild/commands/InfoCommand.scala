package scala.meta.internal.pantsbuild

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import org.typelevel.paiges.Doc
import metaconfig.cli.Messages

object InfoCommand extends Command[InfoOptions]("info") {
  override def description: Doc =
    Doc.paragraph("Display information about an existing project")
  override def options: Doc = Messages.options(InfoOptions())
  def run(info: InfoOptions, app: CliApp): Int = {
    info.names match {
      case Nil =>
        app.error("missing argument [name]")
        1
      case name :: Nil =>
        Project.fromName(name, info.common) match {
          case Some(value) =>
            value.targets.foreach { target =>
              println(target)
            }
            0
          case None =>
            SharedCommand.noSuchProject(name)
        }
      case names =>
        app.error(s"too many arguments '${names.mkString(" ")}'")
        1
    }
  }
}
