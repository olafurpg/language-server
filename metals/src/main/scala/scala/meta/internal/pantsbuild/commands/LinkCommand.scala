package scala.meta.internal.pantsbuild.commands

import metaconfig.cli.Command
import metaconfig.cli.CliApp
import org.typelevel.paiges.Doc
import metaconfig.cli.Messages
import fansi.Color
import java.nio.file.Files

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

  def warnBloopDirectory(
      project: Project,
      common: SharedOptions,
      app: CliApp
  ): Boolean = {
    val bloopDirectory = common.bloopDirectory
    if (Files.isDirectory(bloopDirectory)) {
      app.error(
        s"unable to link project '${project.name}' because '$bloopDirectory' is a directory. " +
          s"\n\tTo fix this problem, run 'rm -rf $bloopDirectory' and run fastpass again."
      )
      true
    } else {
      false
    }
  }
  def run(link: LinkOptions, app: CliApp): Int = {
    SharedCommand.withOneProject(
      "link",
      link.projects,
      link.common,
      app
    ) { project =>
      symlinkToOut(project, link.common, app)
    }
  }

  def symlinkToOut(
      project: Project,
      common: SharedOptions,
      app: CliApp
  ): Int = {
    if (warnBloopDirectory(project, common, app)) {
      1
    } else {
      symlinkToOut(project, common)
      app.out.println(
        s"${Color.LightBlue("info:")} linked project '${project.name}'." +
          "\n\tRun 'bloop projects' to see if all Pants targets have exported correctly."
      )
      0
    }
  }

  private def symlinkToOut(project: Project, common: SharedOptions): Unit = {
    val workspace = common.workspace
    val workspaceBloop = common.bloopDirectory
    val out = project.root.bspRoot.toNIO
    val outBloop = project.root.bloopRoot.toNIO

    if (!Files.exists(workspaceBloop) || Files.isSymbolicLink(workspaceBloop)) {
      val outBloop = out.resolve(".bloop")
      Files.deleteIfExists(workspaceBloop)
      Files.createSymbolicLink(workspaceBloop, outBloop)
    }

    val inScalafmt = {
      val link = workspace.resolve(".scalafmt.conf")
      // Configuration file may be symbolic link.
      val relpath =
        if (Files.isSymbolicLink(link)) Files.readSymbolicLink(link)
        else link
      // Symbolic link may be relative to workspace directory.
      if (relpath.isAbsolute()) relpath
      else workspace.resolve(relpath)
    }
    val outScalafmt = out.resolve(".scalafmt.conf")
    if (!out.startsWith(workspace) &&
      Files.exists(inScalafmt) && {
        !Files.exists(outScalafmt) ||
        Files.isSymbolicLink(outScalafmt)
      }) {
      Files.deleteIfExists(outScalafmt)
      Files.createSymbolicLink(outScalafmt, inScalafmt)
    }
  }
}
