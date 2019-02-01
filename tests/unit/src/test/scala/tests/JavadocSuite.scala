package tests

object Javadoc {
  def toMarkdown(javadoc: String): String = {
    "\\{@code (.*)\\}".r.replaceAllIn(javadoc, "`$1`")
  }
}

object JavadocSuite extends BaseSuite {

  def check(name: String, original: String, expected: String): Unit = {
    test(name) {
      val obtained = Javadoc.toMarkdown(original)
      assertNoDiff(obtained, expected)
    }
  }

  check(
    "basic",
    "a {@code int} value",
    "a `int` value"
  )

}
