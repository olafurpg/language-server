package tests

import java.nio.file.Paths
import scala.meta.internal.metals.JavaBinary
import scala.meta.internal.metals.{BuildInfo => V}
import scala.sys.process._

object BloopLauncher {

  def start(): Unit = {
    execute(
      List(
        JavaBinary(),
        "-jar",
        resource("bloop-launcher"),
        V.bloopVersion,
        "--skip-bsp-connection"
      )
    )
  }

  def stop(): Unit = {
    execute(
      List(
        "python",
        resource("bloop.py"),
        "exit"
      )
    )
  }

  private def execute(sh: List[String]): Unit = {
    val exit = Process(sh).!
    scribe.info(s"exit: $exit '${sh.mkString(" ")}'")
  }

  private def resource(name: String): String = {
    val resource = this.getClass.getResource(s"/$name")
    Paths.get(resource.toURI).toAbsolutePath.toString
  }

}
