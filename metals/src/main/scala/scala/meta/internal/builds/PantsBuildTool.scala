package scala.meta.internal.builds

import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.{MetalsServerConfig, UserConfiguration}

case class PantsBuildTool() extends BuildTool {
  def version: String = "1.0.0"
  def minimumVersion: String = "1.0.0"
  def recommendedVersion: String = "1.0.0"
  def executableName: String = "bash"
  def args(
      workspace: AbsolutePath,
      userConfig: () => UserConfiguration,
      config: MetalsServerConfig
  ): List[String] = {

    val command = List(
      workspace.resolve("pants").toString(),
      "--pants-config-files=pants.ini.scalameta",
      "compile.zinc",
      "--empty-compilation",
      "--cache-ignore",
      "--no-use-classpath-jars",
      "bloop.bloop-export-config",
      "--sources",
      "bloop.bloop-gen",
      "--execution-strategy=subprocess",
      userConfig().pantsTargets.getOrElse("::/")
    ).mkString(" ")

    List(
      "-c",
      command
    )
  }
}
