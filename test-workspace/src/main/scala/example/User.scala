package example

import java.nio.file.Paths

case class User(
    name: String,
    int: Int
)

object User {
  val x: Int = 42
  val path = Paths.get("build.sbt")
}
