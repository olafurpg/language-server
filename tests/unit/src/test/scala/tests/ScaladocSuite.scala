package tests

import scala.meta._
import scala.meta.contrib.ScaladocParser
object Scaladoc {
  def toMarkdown(code: String): String = {
    val docstrings = code.tokenize.get.collect {
      case c: Token.Comment if c.syntax.startsWith("/**") =>
        ScaladocParser.parseScaladoc(c)
    }
    pprint.log(docstrings)
    code
  }
}
object ScaladocSuite extends BaseSuite {

  def check(name: String, original: String, expected: String): Unit = {
    test(name) {
      val obtained = Scaladoc.toMarkdown(original)
      assertNoDiff(obtained, expected)
    }
  }

  check(
    "define",
    """
      |/**
      | * @define description A number
      | */
      |trait Numbers {
      |  /**
      |   * @param a $description
      |   */
      | def number: Int
      |}
    """.stripMargin,
    """
      |/**
      | * @define description A number
      | */
      |trait Numbers {
      |  /**
      |   * @param a A number
      |   */
      | def number: Int
      |}
      |""".stripMargin
  )

}
