package example

import java.nio.file.Paths

case class User(name: String, age: Int)

object User {
  val sum15: Int = 432
  val path = Paths.get("build.sbt")
}
