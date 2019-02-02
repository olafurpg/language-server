package tests

import scala.language.postfixOps
import scala.meta._

object Scaladoc {
  def toMarkdown(code: String): String = {
    val docstrings = code.tokenize.get.collect {
      case c: Token.Comment if c.syntax.startsWith("/**") =>
        ScaladocParser.parseAtSymbol(c.syntax)
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
    "tags",
    """
      |/**
      | *  implicits for converting to and from `Int`.
      | *
      | *  ==Overview==
      | *  The main class to use is [[my.package.complex.Complex]], as so
      | *  {{{
      | *  scala> val complex = Complex(4,3)
      | *  complex: my.package.complex.Complex = 4 + 3i
      | *  }}}
      | *
      | *  If you include [[my.package.complex.ComplexConversions]], you can
      | *  convert numbers more directly
      | *  {{{
      | *  scala> import my.package.complex.ComplexConversions._
      | *  scala> val complex = 4 + 3.i
      | *  complex: my.package.complex.Complex = 4 + 3i
      | *  }}}
      | */
      |}
    """.stripMargin,
    """
      |@param a A number
      |""".stripMargin
  )

}
