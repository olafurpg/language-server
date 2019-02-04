package tests

object CompletionFastSuite extends BaseCompletionSuite {
  override def beforeAll(): Unit = ()
  check(
    "scope",
    """
      |object A {
      |  Lis@@
      |}""".stripMargin,
    """
      |List: collection.immutable.List.type
      |""".stripMargin
  )

  check(
    "member",
    """
      |object A {
      |  List.emp@@
      |}""".stripMargin,
    """
      |empty[A]: List[A]
      |""".stripMargin
  )

  check(
    "extension",
    """
      |object A {
      |  "".stripSu@@
      |}""".stripMargin,
    """|stripSuffix(suffix: String): String
       |""".stripMargin
  )

  check(
    "tparam",
    """
      |object A {
      |  Map.empty[Int,String].applyOr@@
      |}""".stripMargin,
    """|applyOrElse[K1 <: Int, V1 >: String](x: K1,default: K1 => V1): V1
       |""".stripMargin
  )
}
