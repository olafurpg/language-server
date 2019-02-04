package tests

/**
 * Same as SignatureHelpSuite except without indexing library dependency sources.
 *
 * There's a base ~2s overhead for running SignatureHelpSuite because we need to resolve/fetch
 * scala-library.jar and index it's sources in order to extract docstrings and default parameter
 * values. This test suite is intended for test cases that don't need that and can therefore
 * run a bit faster in edit/test/debug workflows.
 */
object SignatureHelpFastSuite extends BaseSignatureHelpSuite {
  override def beforeAll(): Unit = ()
  check(
    "method",
    """
      |object a {
      |  assert(true, ms@@)
      |}
    """.stripMargin,
    """|assert(assertion: Boolean, message: => Any): Unit
       |                           ^^^^^^^^^^^^^^^
       |assert(assertion: Boolean): Unit
       |""".stripMargin
  )
  check(
    "empty",
    """
      |object a {
      |  assert(@@)
      |}
    """.stripMargin,
    """|assert(assertion: Boolean, message: => Any): Unit
       |assert(assertion: Boolean): Unit
       |       ^^^^^^^^^^^^^^^^^^
       |""".stripMargin
  )
  check(
    "erroneous",
    """
      |object a {
      |  Option(1).fold("")(_ => a@@)
      |}
    """.stripMargin,
    """|fold[B](ifEmpty: => B)(f: Int => B): B
       |                       ^^^^^^^^^^^
       |""".stripMargin
  )
  check(
    "canbuildfrom2",
    """
      |object a {
      |  List(1).map(@@)
      |}
    """.stripMargin,
    """|map[B, That](f: Int => B)(bf: scala.collection.generic.CanBuildFrom[List[Int],B,That]): That
       |             ^^^^^^^^^^^
       |""".stripMargin
  )
  check(
    "ctor",
    """
      |object a {
      |  new scala.util.Random(@@)
      |}
    """.stripMargin,
    """|<init>(): scala.util.Random
       |<init>(seed: Int): scala.util.Random
       |<init>(seed: Long): scala.util.Random
       |<init>(self: java.util.Random): scala.util.Random
       |""".stripMargin
  )

  check(
    "apply",
    """
      |object a {
      |  def apply(a: Int): Int = a
      |  def apply(b: String): String = b
      |  a(""@@)
      |}
    """.stripMargin,
    """|apply(b: String): String
       |      ^^^^^^^^^
       |apply(a: Int): Int
       |""".stripMargin
  )
  check(
    "partial",
    """
      |object a {
      |  Option(1).collect {
      |   case@@
      |  }
      |}
    """.stripMargin,
    """|collect[B](pf: PartialFunction[Int,B]): Option[B]
       |           ^^^^^^^^^^^^^^^^^^^^^^^^^^
       |""".stripMargin
  )
  check(
    "nested",
    """
      |object a {
      |  List(Option(1@@))
      |}
    """.stripMargin,
    """|apply[A](x: A): Option[A]
       |         ^^^^
       |""".stripMargin
  )
  check(
    "nested2",
    """
      |object a {
      |  List(Opt@@ion(1))
      |}
    """.stripMargin,
    """|apply[A](xs: A*): List[A]
       |         ^^^^^^
       |""".stripMargin
  )
  check(
    "vararg",
    """
      |object a {
      |  List(1, 2@@
      |}
    """.stripMargin,
    """|apply[A](xs: A*): List[A]
       |         ^^^^^^
       |""".stripMargin
  )
  check(
    "tparam",
    """
      |object a {
      |  identity[I@@]
      |}
    """.stripMargin,
    """|identity[A](x: A): A
       |         ^
       |""".stripMargin
  )
  check(
    "tparam2",
    """
      |object a {
      |  Option.empty[I@@]
      |}
    """.stripMargin,
    """|empty[A]: Option[A]
       |      ^
       |""".stripMargin
  )
  check(
    "tparam3",
    """
      |object a {
      |  Option[I@@]
      |}
    """.stripMargin,
    """|apply[A](x: A): Option[A]
       |      ^
       |""".stripMargin
  )
  check(
    "tparam4",
    """
      |object a {
      |  Map.empty[I@@]
      |}
    """.stripMargin,
    """|empty[K, V]: scala.collection.immutable.Map[K,V]
       |      ^
       |""".stripMargin
  )
  check(
    "tparam5",
    """
      |object a {
      |  List[String](1).lengthCompare(@@)
      |}
    """.stripMargin,
    """|lengthCompare(len: Int): Int
       |              ^^^^^^^^
       |""".stripMargin
  )
  check(
    "error1",
    """
      |object a {
      |  Map[Int](1 @@-> "").map {
      |  }
      |}
    """.stripMargin,
    ""
  )
  check(
    "for",
    """
      |object a {
      |  for {
      |    i <- Option(1)
      |    j < 1.to(i)
      |    if i > j
      |    k@@ = i + j
      |    l <- j.to(k)
      |  } yield l
      |}
    """.stripMargin,
    """|flatMap[B](f: Int => Option[B]): Option[B]
       |           ^^^^^^^^^^^^^^^^^^^
       |""".stripMargin
  )

  check(
    "bounds",
    """
      |object a {
      |  Map.empty[Int, String].applyOrElse(1@@)
      |}
    """.stripMargin,
    """|applyOrElse[K1 <: Int, V1 >: String](x: K1, default: K1 => V1): V1
       |                                     ^^^^^
       |""".stripMargin
  )

  check(
    "error",
    """
      |object a {
      |  Map[Int](1 @@-> "").map {
      |  }
      |}
    """.stripMargin,
    ""
  )

  check(
    "named",
    """
      |case class User(name: String = "John", age: Int = 42)
      |object A {
      |  User(age = 1, @@)
      |}
    """.stripMargin,
    """|apply(<age: Int = {}>, <name: String = {}>): User
       |                       ^^^^^^^^^^^^^^^^^^^
       |""".stripMargin
  )
  check(
    "named1",
    """
      |case class User(name: String = "John", age: Int = 42)
      |object A {
      |  User(name = "", @@)
      |}
    """.stripMargin,
    """|apply(name: String = {}, age: Int = {}): User
       |                         ^^^^^^^^^^^^^
       |""".stripMargin
  )
  check(
    "named2",
    """
      |object A {
      |  def user(name: String, age: Int) = age
      |  user(na@@me = "", age = 42)
      |}
    """.stripMargin,
    """|user(name: String, age: Int): Int
       |     ^^^^^^^^^^^^
       |""".stripMargin
  )
  check(
    "named3",
    """
      |object A {
      |  def user(name: String, age: Int): Int = age
      |  def user(name: String, age: Int, street: Int): Int = age
      |  def x = user(str@@eet = 42, name = "", age = 2)
      |}
    """.stripMargin,
    """|user(name: String, age: Int, street: Int): Int
       |     ^^^^^^^^^^^^
       |user(name: String, age: Int): Int
       |""".stripMargin
  )
}
