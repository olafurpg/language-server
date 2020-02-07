package scala.meta.internal.pantsbuild

import metaconfig.Conf
import metaconfig.generic
import scala.meta.internal.io.PathIO
import java.nio.file.Path
import scala.meta.internal.pantsbuild.Codecs._
import metaconfig.annotation._
import metaconfig.generic.Settings
import metaconfig.Configured
import metaconfig.ConfDecoder
import java.nio.file.Paths
import scala.meta.io.AbsolutePath

sealed abstract class Args
object Args {
  def helpMessage: String = {
    """|Usage: fastpass [OPTIONS] COMMAND
       |
       |Commands:
       |  help                                              Print this help message.
       |  list                                              List all projects.
       |  info PROJECT_NAME                                 Print information about an existing project.
       |  create [--name PROJECT_NAME] [PANTS_TARGETS ..]   Create new project.
       |  refresh PROJECT_NAME                              Refresh existing project.
       |
       |Run 'fastpass COMMAND --help' for information on a command.
       |""".stripMargin
  }

  case class Subcommand[T](name: String*)(
      implicit val settings: Settings[T],
      implicit val decoder: ConfDecoder[T]
  ) {
    type Value = T
  }

  def parse(args: List[String]): Configured[Args] = {
    val subcommands = List[Subcommand[_ <: Args]](
      Subcommand("help", "-h", "--help")(Help.settings, Help.decoder),
      Subcommand("create")(Create.settings, Create.decoder),
      Subcommand("refresh")(Refresh.settings, Refresh.decoder),
      Subcommand("list")(ListProjects.settings, ListProjects.decoder)
    )

    args match {
      case Nil => Configured.error(helpMessage)
      case head :: tail =>
        subcommands.find(_.name.contains(head)) match {
          case Some(subcommand) =>
            Conf
              .parseCliArgs(tail)(subcommand.settings)
              .andThen(_.as(subcommand.decoder))
          case None =>
            Configured.error(s"unknown command: $head\n$helpMessage")
        }
    }
  }
}

case class Help() extends Args
object Help {
  val default = Help()
  implicit lazy val surface = generic.deriveSurface[Help]
  implicit lazy val encoder = generic.deriveEncoder[Help]
  implicit lazy val decoder = generic.deriveDecoder[Help](default)
  implicit lazy val settings = Settings[Help]
}

case class Common(
    @Description("Print this help message.")
    help: Boolean = false,
    @Description("The root directory of the Pants build.")
    workspace: Path = PathIO.workingDirectory.toNIO
) {
  val pants = AbsolutePath(workspace.resolve("pants"))
  val home = AbsolutePath {
    Option(System.getenv("FASTPASS_HOME")) match {
      case Some(value) => Paths.get(value)
      case None => workspace.resolveSibling("intellij-bsp")
    }
  }
}
object Common {
  val default = Common()
  implicit lazy val surface = generic.deriveSurface[Common]
  implicit lazy val encoder = generic.deriveEncoder[Common]
  implicit lazy val decoder = generic.deriveDecoder[Common](default)
  implicit lazy val settings = Settings[Common]
}

case class Refresh(
    name: String = "",
    @Inline common: Common = Common()
) extends Args
object Refresh {
  val default = Refresh()
  implicit lazy val surface = generic.deriveSurface[Refresh]
  implicit lazy val encoder = generic.deriveEncoder[Refresh]
  implicit lazy val decoder = generic.deriveDecoder[Refresh](default)
  implicit lazy val settings = Settings[Refresh]
}

case class Info(
    name: String,
    @Inline common: Common = Common()
) extends Args

case class ListProjects(
    @Inline common: Common = Common()
) extends Args
object ListProjects {
  val default = ListProjects()
  implicit lazy val surface = generic.deriveSurface[ListProjects]
  implicit lazy val encoder = generic.deriveEncoder[ListProjects]
  implicit lazy val decoder = generic.deriveDecoder[ListProjects](default)
  implicit lazy val settings = Settings[ListProjects]
}

case class Create(
    name: String = "",
    @ExtraName("remainingArgs")
    targets: List[String] = Nil,
    @Inline common: Common = Common.default
) extends Args

object Create {
  val default = Create()
  implicit lazy val surface = generic.deriveSurface[Create]
  implicit lazy val encoder = generic.deriveEncoder[Create]
  implicit lazy val decoder = generic.deriveDecoder[Create](default)
  implicit lazy val settings = Settings[Create]
}
