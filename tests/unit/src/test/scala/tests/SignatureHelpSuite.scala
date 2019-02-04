package tests

object SignatureHelpSuite extends BaseSignatureHelpSuite {
  checkDoc(
    "curry",
    """
      |object a {
      |  Option(1).fold("")(_ => @@)
      |}
    """.stripMargin,
    """|
       |fold[B](ifEmpty: => B)(f: Int => B): B
       |                       ^^^^^^^^^^^
       |  @param f Int => ???
       |""".stripMargin
  )

  checkDoc(
    "curry2",
    """
      |object a {
      |  Option(1).fold("@@")
      |}
    """.stripMargin,
    """|
       |fold[B](ifEmpty: => B)(f: Int => B): B
       |        ^^^^^^^^^^^^^
       |  @param ifEmpty String
       |""".stripMargin
  )
  checkDoc(
    "curry3",
    """
      |object a {
      |  List(1).foldLeft(0) {
      |   case @@
      |  }
      |}
    """.stripMargin,
    """|
       |foldLeft[B](z: B)(op: (B, Int) => B): B
       |                  ^^^^^^^^^^^^^^^^^
       |  @param op (Int, Int) => Int
       |""".stripMargin
  )
  checkDoc(
    "curry4",
    """
      |object a {
      |  def curry(a: Int, b: Int)(c: Int) = a
      |  curry(1)(3@@)
      |}
    """.stripMargin,
    """|
       |curry(a: Int, b: Int)(c: Int): Int
       |                      ^^^^^^
       |""".stripMargin
  )
  checkDoc(
    "canbuildfrom",
    """
      |object a {
      |  List(1).map(x => @@)
      |}
    """.stripMargin,
    """|
       |map[B, That](f: Int => B)(bf: scala.collection.generic.CanBuildFrom[List[Int],B,That]): That
       |             ^^^^^^^^^^^
       |  @param f Int => ???
       |""".stripMargin
  )
  checkDoc(
    "too-many",
    """
      |object a {
      |  Option(1, 2, @@2)
      |}
    """.stripMargin,
    """|apply[A](x: A): Option[A]
       |         ^^^^
       |  @param x (Int, Int, Int)
       |""".stripMargin
  )
  checkDoc(
    "java5",
    """
      |object a {
      |  java.util.Collections.singleton(@@)
      |}
    """.stripMargin,
    """| Returns an immutable set containing only the specified object.
       |The returned set is serializable.
       |singleton[T](o: T): java.util.Set[T]
       |             ^^^^
       |  @param T <T> the class of the objects in the set
       |  @param o o the sole object to be stored in the returned set.
       |""".stripMargin
  )
  checkDoc(
    "default",
    """
      |object A {
      |  new scala.util.control.Exception.Catch(@@)
      |}
    """.stripMargin,
    """|A container class for catch/finally logic.
       |<init>(T: util.control.Exception.Catcher[T], pf: Option[util.control.Exception.Finally] = {}, fin: Throwable => Boolean = None): scala.util.control.Exception.Catch[T]
       |       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
       |  @param T result type of bodies used in try and catch blocks
       |  @param pf Partial function used when applying catch logic to determine result value
       |  @param fin ally logic.
       |""".stripMargin
  )
  check(
    "java",
    """
      |object a {
      |  new java.io.File(@@)
      |}
    """.stripMargin,
    """|<init>(uri: java.net.URI): java.io.File
       |<init>(parent: java.io.File, child: String): java.io.File
       |<init>(parent: String, child: String): java.io.File
       |<init>(pathname: String): java.io.File
       |""".stripMargin
  )
  check(
    "java2",
    """
      |object a {
      |  "".substring(1@@)
      |}
    """.stripMargin,
    """|substring(beginIndex: Int, endIndex: Int): String
       |substring(beginIndex: Int): String
       |          ^^^^^^^^^^^^^^^
       |""".stripMargin
  )
  check(
    "java3",
    """
      |object a {
      |  String.valueOf(1@@)
      |}
    """.stripMargin,
    """|valueOf(d: Double): String
       |valueOf(f: Float): String
       |valueOf(l: Long): String
       |valueOf(i: Int): String
       |        ^^^^^^
       |valueOf(c: Char): String
       |valueOf(b: Boolean): String
       |valueOf(data: Array[Char], offset: Int, count: Int): String
       |valueOf(data: Array[Char]): String
       |valueOf(obj: Any): String
       |""".stripMargin
  )
  check(
    "java4",
    """
      |object a {
      |  String.valueOf(@@)
      |}
    """.stripMargin,
    """|valueOf(d: Double): String
       |        ^^^^^^^^^
       |valueOf(f: Float): String
       |valueOf(l: Long): String
       |valueOf(i: Int): String
       |valueOf(c: Char): String
       |valueOf(b: Boolean): String
       |valueOf(data: Array[Char]): String
       |valueOf(obj: Any): String
       |""".stripMargin
  )
}
