package scala.meta.internal.pantsbuild

import scala.meta.io.AbsolutePath
import scala.sys.process._
import ujson.Obj
import scala.meta.internal.metals.BuildInfo
import ujson.Str
import scala.meta.internal.metals.MetalsEnrichments._
import scala.util.control.NonFatal

object VSCode {
  def launch(args: Export): Unit =
    try {
      val settings = AbsolutePath(args.out)
        .resolve(".vscode")
        .resolve("settings.json")
      val oldSettings = readSettings(settings)
      oldSettings("metals.serverVersion") = BuildInfo.metalsVersion
      oldSettings("metals.pantsTargets") = args.targets.map(Str(_))
      oldSettings("metals.bloopVersion") = BuildInfo.bloopNightlyVersion
      settings.writeText(ujson.write(oldSettings, indent = 2))
      scribe.info(s"updated: $settings")
      val code = codeCommand()
      exec(code, "--install-extension", "scalameta.metals")
      exec(code, "--new-window", args.out.toString())
      findFileToOpen(args).headOption.foreach { file =>
        exec(code, "--reuse-window", file.toString())
      }
    } catch {
      case NonFatal(e) =>
        val isCodeNotFound = Option(e.getMessage())
          .exists(_.contains("Cannot run program \"code\""))
        if (isCodeNotFound) {
          scribe.error(
            "The command 'code' is not installed on this computer. " +
              "To fix this problem, install VS Code from https://code.visualstudio.com/download, " +
              "execute the 'Install \"code\" command in PATH' command and then try running again."
          )
        } else {
          scribe.error("failed to launch VS Code", e)
        }
    }

  private def codeCommand(): String = {
    val applications = AbsolutePath("/Applications")
    val app = List(
      applications.resolve("Visual Studio Code.app"),
      applications.resolve("Visual Studio Code - Insiders.app")
    ).map(
      _.resolve("Contents")
        .resolve("Resources")
        .resolve("app")
        .resolve("bin")
        .resolve("code")
    )
    app
      .collectFirst {
        case file if file.isFile && file.toFile.canExecute() =>
          file.toString
      }
      .getOrElse("code")
  }

  private def exec(command: String*): Unit = {
    val exit = command.!
    require(exit == 0, s"command failed: ${command.mkString(" ")}")
  }

  private def findFileToOpen(args: Export): List[AbsolutePath] = {
    val readonly = args.out.resolve(".metals")
    for {
      root <- PantsConfiguration.sourceRoots(
        AbsolutePath(args.workspace),
        args.targets
      )
      file <- root.listRecursive
        .filter(_.isScala)
        .filter(!_.toNIO.startsWith(readonly))
        .take(1)
        .headOption
    } yield file
  }
  private def readSettings(settings: AbsolutePath): Obj = {
    if (settings.isFile) {
      ujson.read(settings.readText).obj
    } else {
      Obj()
    }
  }
}
