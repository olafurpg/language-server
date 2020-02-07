package tests.pantsbuild

import tests.BaseSuite
import scala.meta.internal.pantsbuild._

class ArgsSuite extends BaseSuite {
  def checkParse(name: String, args: List[String], expected: Args): Unit = {
    test(name) {
      val obtained = Args.parse(args).get
      assertEquals(obtained, expected)
    }
  }

  checkParse(
    "help",
    List("--help"),
    Help()
  )

  checkParse(
    "help",
    List("-h"),
    Help()
  )

  checkParse(
    "help",
    List("help"),
    Help()
  )

  checkParse(
    "list",
    List("list"),
    ListProjects()
  )
}
