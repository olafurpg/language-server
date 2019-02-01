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
    docstrings.last
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
      |/**
      |  * @param a $description
      |  */
      |}
    """.stripMargin,
    """
      |@param a A number
      |""".stripMargin
  )

}
