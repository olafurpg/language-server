package tests

object CompletionSuite extends BasePCSuite {
  def check(name: String, original: String, expected: String): Unit = {
    test(name) {
      val (code, offset) = params(original)
      val obtained = pc.complete("A.scala", code, offset)
      pprint.log(obtained)
    }
  }

  check(
    "basic",
    """
      |
      |object A {
      |  String.forma@@
      |}
      |""".stripMargin,
    """"""
  )
}
