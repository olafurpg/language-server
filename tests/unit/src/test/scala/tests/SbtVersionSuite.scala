package tests

import scala.meta.internal.metals.SbtVersion
import scala.meta.testkit.StringFS

object SbtVersionSuite extends BaseSuite {
  def check(
      layout: String,
      expected: String,
  ): Unit = {
    test(expected) {
      val root = StringFS.fromString(layout)
      val obtained = SbtVersion(root).version
      assertNoDiff(obtained, expected)
    }
  }

  def checkSupported(version: String): Unit = {
    test(s"supported   - $version") {
      val isSupported = SbtVersion.isSupported(version)
      assert(isSupported)
    }
  }

  def checkUnsupported(version: String): Unit = {
    test(s"unsupported - $version") {
      val isSupported = SbtVersion.isSupported(version)
      assert(!isSupported)
    }
  }

  check(
    """
      |/project/build.properties
      |sbt.version=0.13
      """.stripMargin,
    "0.13"
  )

  check(
    """
      |/project/build.properties
      |sbt.version=1.1.3
    """.stripMargin,
    "1.1.3"
  )

  checkSupported("1.2.3")
  checkUnsupported("1.2.0")
  checkUnsupported("1.1.0")
  checkUnsupported("0.13.16")

}
