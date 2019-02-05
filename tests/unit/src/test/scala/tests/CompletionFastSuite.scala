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
      |class Foo[A] {
      |  def identity[B >: A](a: B): B = a
      |}
      |object Foo {
      |  new Foo[Int].ident@@
      |}""".stripMargin,
    """|identity[B >: Int](a: B): B
       |""".stripMargin
  )

  check(
    "tparam1",
    """
      |class Foo[A] {
      |  def identity(a: A): A = a
      |}
      |object Foo {
      |  new Foo[Int].ident@@
      |}""".stripMargin,
    """|identity(a: Int): Int
       |""".stripMargin
  )
  check(
    "tparam2",
    """
      |object A {
      |  Map.empty[Int, String].getOrEl@@
      |}""".stripMargin,
    """|getOrElse[V1 >: String](key: Int,default: => V1): V1
       |""".stripMargin
  )

  check(
    "cursor",
    """
      |object A {
      |  val default = 1
      |  def@@
      |}""".stripMargin,
    """|default: Int
       |""".stripMargin
  )
}
