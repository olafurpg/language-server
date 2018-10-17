package scala.meta.internal.metals

import java.nio.file.Files
import java.util.Properties
import scala.meta.io.AbsolutePath

object SbtVersion {
  def apply(workspace: AbsolutePath): BuildTool.Sbt = {
    val props = new Properties()
    val buildproperties =
      workspace.resolve("project").resolve("build.properties")
    val version =
      if (!buildproperties.isFile) None
      else {
        val in = Files.newInputStream(buildproperties.toNIO)
        try props.load(in)
        finally in.close()
        Option(props.getProperty("sbt.version"))
      }
    BuildTool.Sbt(version.getOrElse(unknown))
  }
  private def unknown = "<unknown>"

  def isSupported(version: String): Boolean = {
    !version.startsWith(unknown) &&
    !version.startsWith("0.") &&
    !version.startsWith("1.0") &&
    !version.startsWith("1.1") &&
    !version.startsWith("1.2.0")
  }
}
