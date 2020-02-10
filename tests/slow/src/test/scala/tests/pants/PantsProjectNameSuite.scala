package tests.pants

import tests.BaseSuite
import scala.meta.internal.pantsbuild.PantsConfiguration

class PantsProjectNameSuite extends BaseSuite {
  def checkTargetsName(
      name: String,
      targets: List[String],
      expected: String
  ): Unit = {
    test(name) {
      val obtained = PantsConfiguration.outputFilename(targets)
      assertNoDiff(obtained, expected)
    }
  }

  checkTargetsName(
    "basic",
    List("stats::"),
    "stats"
  )

  checkTargetsName(
    "two",
    List("stats::", "cache::"),
    "stats__cache"
  )

  checkTargetsName(
    "two-nested",
    List("stats/server::", "cache/server::"),
    "stats__cache"
  )
}
