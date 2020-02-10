package scala.meta.internal.pantsbuild.commands

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import org.typelevel.paiges.Doc
import scala.meta.internal.pantsbuild.Export

object CreateCommand extends Command[CreateOptions]("create") {
  override def description: Doc = Doc.paragraph("Create a new project")
  override def usage: Doc =
    Doc.text("fastpass create [OPTIONS] [TARGETS ...]")
  def run(create: CreateOptions, app: CliApp): Int = {
    val name = create.actualName
    Project.fromName(name, create.common) match {
      case Some(value) =>
        app.error(
          s"project '${create.name}' already exists.\n\tDid you mean 'fastpass refresh ${create.name}'?"
        )
        1
      case None =>
        SharedCommand.interpretExport(
          Export().copy(
            workspace = create.common.workspace,
            projectName = Some(name),
            targets = create.targets,
            out = create.common.home
              .resolve(name)
              .resolve(name)
              .toNIO
          )
        )
    }
  }
}
