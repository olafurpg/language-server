package tests.hover

import tests.pc.BaseHoverSuite

object HoverDefnSuite extends BaseHoverSuite {

  check(
    "val",
    """object a {
      |  <<val @@x = List(1)>>
      |}
      |""".stripMargin,
    """
      |```scala
      |val x: List[Int]
      |```
      |""".stripMargin
  )

  check(
    "var",
    """object a {
      |  <<var @@x = List(1)>>
      |}
      |""".stripMargin,
    """
      |```scala
      |var x: List[Int]
      |```
      |""".stripMargin
  )

  check(
    "def-nullary",
    """object a {
      |  <<def @@x = List(1)>>
      |}
      |""".stripMargin,
    """
      |```scala
      |def x: List[Int]
      |```
      |""".stripMargin
  )

  check(
    "def-params",
    """object a {
      |  <<def @@method(x: Int) = List(x)>>
      |}
      |""".stripMargin,
    """
      |```scala
      |def method(x: Int): List[Int]
      |```
      |```scala
      |List[Int]
      |```
      |""".stripMargin
  )

  check(
    "def-tparams",
    """object a {
      |  <<def @@empty[T] = Option.empty[T]>>
      |}
      |""".stripMargin,
    """
      |```scala
      |def empty[T]: Option[T]
      |```
      |```scala
      |Option[T]
      |```
      |""".stripMargin
  )

  check(
    "context-bound",
    """object a {
      |  <<def @@empty[T:Ordering] = Option.empty[T]>>
      |}
      |""".stripMargin,
    """
      |```scala
      |def empty[T: Ordering]: Option[T]
      |```
      |```scala
      |Option[T]
      |```
      |""".stripMargin
  )

  check(
    "lambda-param",
    """object a {
      |  List(1).map(<<@@x>> => )
      |}
      |""".stripMargin,
    """|```scala
       |x: Int
       |```
       |""".stripMargin
  )

  check(
    "param",
    """object a {
      |  def method(<<@@x: Int>>): Int = x
      |}
      |""".stripMargin,
    """|```scala
       |x: Int
       |```
       |""".stripMargin
  )

  check(
    "ctor",
    """class a {
      |  <<def t@@his(x: Int) = this()>>
      |}
      |""".stripMargin,
    """|```scala
       |def this(x: Int): a
       |```
       |""".stripMargin
  )

  check(
    "ctor-param",
    """class a {
      |  def this(<<@@x: Int>>) = this()
      |}
      |""".stripMargin,
    """|```scala
       |x: Int
       |```
       |""".stripMargin
  )

  check(
    "implicit-param",
    """class a {
      |  def method(implicit <<@@x: Int>>) = this()
      |}
      |""".stripMargin,
    """|```scala
       |implicit x: Int
       |```
       |""".stripMargin
  )

  check(
    "implicit-param2",
    """class a {
      |  def method(implicit y: Int, <<@@x: Int>>) = this()
      |}
      |""".stripMargin,
    """|```scala
       |implicit x: Int
       |```
       |""".stripMargin
  )

  check(
    "object",
    """object M@@yObject
      |""".stripMargin,
    ""
  )

  check(
    "trait",
    """trait M@@yTrait
      |""".stripMargin,
    ""
  )

  check(
    "class",
    """trait M@@yClass
      |""".stripMargin,
    ""
  )

}
