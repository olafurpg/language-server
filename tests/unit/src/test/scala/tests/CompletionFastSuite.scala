package tests

object CompletionFastSuite extends BaseCompletionSuite {
  override def beforeAll(): Unit = ()
  check(
    "scope",
    """
      |object A {
      |  Lis@@
      |}""".stripMargin,
    ""
  )
}
