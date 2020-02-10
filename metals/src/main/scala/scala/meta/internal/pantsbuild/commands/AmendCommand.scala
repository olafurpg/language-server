package scala.meta.internal.pantsbuild.commands

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import org.typelevel.paiges.Doc
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import scala.meta.internal.pantsbuild.IntelliJ
import metaconfig.cli.Messages
import java.io.PrintWriter
import java.nio.file.Path
import scala.util.control.NonFatal
import scala.collection.JavaConverters._

object AmendCommand extends Command[AmendOptions]("amend") {
  override def description: Doc =
    Doc.paragraph(
      "Edit the Pants targets of an existing project"
    )
  override def options: Doc = Messages.options(AmendOptions())
  def run(amend: AmendOptions, app: CliApp): Int = {
    SharedCommand.withOneProject(
      "amend",
      amend.projects,
      amend.common,
      app
    ) { project =>
      Option(System.getenv("EDITOR")) match {
        case None =>
          app.error(
            "the $EDITOR environment variable is undefined. " +
              "To fix this problem, run `export EDITOR=vim` " +
              "(or `export EDITOR='code -w'` for VS Code) " +
              "and try again"
          )
          1
        case Some(editor) =>
          val tmp = Files.write(
            Files.createTempFile("fastpass", project.name),
            project.targets
              .mkString(
                "",
                "\n",
                "\n# Please add or remove targets from this list.\n" +
                  "# When you're done, save the file and close the editor.\n" +
                  "# Lines starting with '#' will be ignored."
              )
              .getBytes(StandardCharsets.UTF_8)
          )
          val exit = editFile(editor, tmp)
          if (exit != 0) {
            app.error(s"failed to amend '${project.name}'")
            exit
          } else {
            val newTargets = Files
              .readAllLines(tmp)
              .asScala
              .flatMap { line =>
                if (line.startsWith("#")) Nil
                else line.split(" ").toList
              }
              .toList
            if (newTargets.isEmpty) {
              app.error("aborting amend since the new target list is empty.")
              1
            } else {
              val newProject = project.copy(targets = newTargets)
              if (newTargets != project.targets) {
                IntelliJ.writeBsp(newProject)
                pprint.log(Project.fromName(project.name, amend.common))
                // RefreshCommand.run(
                //   RefreshOptions(amend.projects, common = amend.common),
                //   app
                // )
                0
              } else {
                app.error(
                  s"aborting amend operation since there is nothing to change. " +
                    "\n\tTo refresh the project, run 'fastpass refresh ${project.name}'"
                )
                0
              }
            }
          }
      }
    }
  }

  private def editFile(editor: String, tmp: Path): Int = {
    try {
      // Adjusted from https://stackoverflow.com/questions/29733038/running-interactive-shell-program-in-java
      val proc = Runtime.getRuntime().exec("/bin/bash")
      val stdin = proc.getOutputStream()
      val pw = new PrintWriter(stdin)
      pw.println(s"code -w $tmp < /dev/tty > /dev/tty")
      pw.close()
      proc.waitFor()
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        1
    }
  }
}
