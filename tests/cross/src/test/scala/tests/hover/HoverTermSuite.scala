package tests.hover

import tests.pc.BaseHoverSuite

object HoverTermSuite extends BaseHoverSuite {
  check(
    "map",
    """object a {
      |  <<List(1).ma@@p(x => x.toString)>>
      |}
      |""".stripMargin,
    """|```scala
       |final override def map[B, That](f: Int => B)(implicit bf: CanBuildFrom[List[Int],B,That]): That
       |```
       |```scala
       |List[String]
       |```
       |""".stripMargin
  )

  check(
    "apply",
    """object a {
      |  <<Li@@st(1)>>.map(x => x.toString)
      |}
      |""".stripMargin,
    """|```scala
       |override def apply[A](xs: A*): List[A]
       |```
       |```scala
       |List[Int]
       |```
       |""".stripMargin
  )

  check(
    "case-apply",
    """case class Person(name: String)
      |object a {
      |  <<Per@@son("")>>
      |}
      |""".stripMargin,
    """|```scala
       |def apply(name: String): Person
       |```
       |```scala
       |Person
       |```
       |""".stripMargin
  )

  check(
    "interpolator-arg",
    """
      |object a {
      |  val name = "John"
      |  <<s"Hello ${na@@me}">>
      |}
      |""".stripMargin,
    """|```scala
       |val name: String
       |```
       |""".stripMargin
  )

  check(
    "interpolator-name",
    """
      |object a {
      |  val name = "John"
      |  <<@@s"Hello ${name}">>
      |}
      |""".stripMargin,
    """|```scala
       |def s(args: Any*): String
       |```
       |```scala
       |String
       |```
       |""".stripMargin
  )

  check(
    "interpolator-macro",
    """
      |object a {
      |  val height = 1.9d
      |  val name = "James"
      |  <<@@f"$name%s is $height%2.2f meters tall">>
      |}
      |""".stripMargin,
    """|```scala
       |def f[A >: Any](args: A*): String = macro
       |```
       |```scala
       |String
       |```
       |""".stripMargin
  )

  check(
    "interpolator-apply",
    """
      |object a {
      |  implicit class Xtension(s: StringContext) {
      |    object num {
      |      def apply[T](a: T)(implicit ev: Int): T = ???
      |    }
      |  }
      |  implicit val n = 42
      |  <<@@num"Hello $n">>
      |}
      |""".stripMargin,
    """|```scala
       |def apply[T](a: T)(implicit ev: Int): T
       |```
       |```scala
       |Int
       |```
       |""".stripMargin
  )

  check(
    "interpolator-unapply",
    """
      |object a {
      |  implicit class Xtension(s: StringContext) {
      |    object num {
      |      def unapply(a: Int): Option[Int] = ???
      |    }
      |  }
      |  42 match {
      |    case nu@@m"$n" =>
      |  }
      |}
      |""".stripMargin,
    """|```scala
       |def unapply(a: Int): Option[Int]
       |```
       |```scala
       |Int
       |```
       |""".stripMargin
  )

  check(
    "new",
    """
      |class Foo(name: String, age: Int)
      |object a {
      |  <<new Fo@@o("", 42)>>
      |}
      |""".stripMargin,
    """|```scala
       |def this(name: String, age: Int): Foo
       |```
       |""".stripMargin
  )

  check(
    "new-tparam",
    """
      |class Foo[T](name: String, age: T)
      |object a {
      |  <<new Fo@@o("", 42)>>
      |}
      |""".stripMargin,
    """|```scala
       |def this(name: String, age: T): Foo[T]
       |```
       |""".stripMargin
  )

  check(
    "new-anon",
    """
      |class Foo(name: String, age: Int)
      |object a {
      |  new Fo@@o("", 42) {
      |    val x = 2
      |  }
      |}
      |""".stripMargin,
    ""
  )

  check(
    "for-guard",
    """
      |object a {
      |  for {
      |    x <- List(1)
      |    if <<@@x>> > 2
      |  } yield x
      |}
      |""".stripMargin,
    """|```scala
       |x: Int
       |```
       |""".stripMargin
  )

  check(
    "for-flatMap",
    """
      |object a {
      |  <<for {
      |    x <@@- Option(1)
      |    if x > 2
      |    y <- Some(x)
      |  } yield x.toString>>
      |}
      |""".stripMargin,
    """|```scala
       |def flatMap[B](f: Int => Option[B]): Option[B]
       |```
       |```scala
       |Option[String]
       |```
       |""".stripMargin
  )

  check(
    "for-map",
    """
      |object a {
      |  for {
      |    x <- Option(1)
      |    if x > 2
      |    <<y <@@- Some(x)
      |  } yield x.toString>>
      |}
      |""".stripMargin,
    """|```scala
       |final def map[B](f: Int => B): Option[B]
       |```
       |```scala
       |Option[String]
       |```
       |""".stripMargin
  )

  check(
    "for-keyword",
    """
      |object a {
      |  <<fo@@r {
      |    x <- Option(1)
      |    if x > 2
      |    y <- Some(x)
      |  } yield x.toString>>
      |}
      |""".stripMargin,
    """|```scala
       |def flatMap[B](f: Int => Option[B]): Option[B]
       |```
       |```scala
       |Option[String]
       |```
       |""".stripMargin
  )

  check(
    "for-yield-keyword",
    """
      |object a {
      |  for {
      |    x <- Option(1)
      |    if x > 2
      |    <<y <- Some(x.toLong)
      |  } yi@@eld x.toString>>
      |}
      |""".stripMargin,
    """|```scala
       |final def map[B](f: Long => B): Option[B]
       |```
       |```scala
       |Option[String]
       |```
       |""".stripMargin
  )

  check(
    "for-if-keyword",
    """
      |object a {
      |  for {
      |    x <- <<Option(1)
      |    i@@f x > 2>>
      |    y <- Some(x)
      |  } yield x.toString
      |}
      |""".stripMargin,
    """|```scala
       |final def withFilter(p: Int => Boolean): Option[Int]#WithFilter
       |```
       |```scala
       |Option[Int]#WithFilter
       |```
       |""".stripMargin
  )

}
