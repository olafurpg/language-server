package scala.meta.internal.pantsbuild

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import org.typelevel.paiges.Doc

object CreateCommand extends Command[CreateOptions]("create") {
  override def description: Doc = Doc.paragraph("Create a new project")
  def run(create: CreateOptions, app: CliApp): Int = {
    Project.fromName(create.name, create.common) match {
      case Some(value) =>
        app.error(
          s"project '${create.name}' already exists.\n\tDid you mean 'fastpass refresh ${create.name}'?"
        )
        1
      case None =>
        SharedCommand.interpretExport(
          Export().copy(
            workspace = create.common.workspace,
            projectName = Some(create.name),
            targets = create.targets,
            out = create.common.home
              .resolve(create.name)
              .resolve(create.name)
              .toNIO
          )
        )
    }
  }
}
