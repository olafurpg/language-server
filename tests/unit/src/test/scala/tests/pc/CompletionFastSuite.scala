package tests.pc

import tests.BaseCompletionSuite

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

  check(
    "dot",
    """
      |object A {
      |  List.@@
      |}""".stripMargin,
    """|apply[A](xs: A*): List[A]
       |canBuildFrom[A]: scala.collection.generic.CanBuildFrom[scala.collection.immutable.List.Coll,A,List[A]]
       |empty[A]: List[A]
       |newBuilder[A]: scala.collection.mutable.Builder[A,List[A]]
       |GenericCanBuildFrom[A <: <?>] extends CanBuildFrom[List[_],A,List[A]]
       |ReusableCBF: scala.collection.immutable.List.GenericCanBuildFrom[Nothing]
       |concat[A](xss: Traversable[A]*): List[A]
       |fill[A](n1: Int,n2: Int)(elem: => A): List[List[A]]
       |fill[A](n1: Int,n2: Int,n3: Int)(elem: => A): List[List[List[A]]]
       |fill[A](n1: Int,n2: Int,n3: Int,n4: Int)(elem: => A): List[List[List[List[A]]]]
       |fill[A](n1: Int,n2: Int,n3: Int,n4: Int,n5: Int)(elem: => A): List[List[List[List[List[A]]]]]
       |fill[A](n: Int)(elem: => A): List[A]
       |iterate[A](start: A,len: Int)(f: A => A): List[A]
       |range[T](start: T,end: T)(implicit evidence$1: Integral[T]): List[T]
       |range[T](start: T,end: T,step: T)(implicit evidence$2: Integral[T]): List[T]
       |tabulate[A](n1: Int,n2: Int)(f: (Int, Int) => A): List[List[A]]
       |tabulate[A](n1: Int,n2: Int,n3: Int)(f: (Int, Int, Int) => A): List[List[List[A]]]
       |tabulate[A](n1: Int,n2: Int,n3: Int,n4: Int)(f: (Int, Int, Int, Int) => A): List[List[List[List[A]]]]
       |tabulate[A](n1: Int,n2: Int,n3: Int,n4: Int,n5: Int)(f: (Int, Int, Int, Int, Int) => A): List[List[List[List[List[A]]]]]
       |tabulate[A](n: Int)(f: Int => A): List[A]
       |unapplySeq[A](x: List[A]): Some[List[A]]
       |->[B](y: B): (A, B)
       |+(other: String): String
       |ensuring(cond: A => Boolean): A
       |ensuring(cond: A => Boolean,msg: => Any): A
       |ensuring(cond: Boolean): A
       |ensuring(cond: Boolean,msg: => Any): A
       |formatted(fmtstr: String): String
       |asInstanceOf[T0]: T0
       |equals(x$1: Any): Boolean
       |getClass(): Class[_]
       |hashCode(): Int
       |isInstanceOf[T0]: Boolean
       |synchronized[T0](x$1: T0): T0
       |toString(): String
       |""".stripMargin
  )

  check(
    "implicit-class",
    """
      |object A {
      |  implicit class XtensionMethod(a: Int) {
      |    def increment = a + 1
      |  }
      |  Xtension@@
      |}""".stripMargin,
    """|XtensionMethod(a: Int): A.XtensionMethod
       |""".stripMargin
  )
  check(
    "fuzzy",
    """
      |object A {
      |  def userService = 1
      |  uService@@
      |}""".stripMargin,
    """|userService: Int
       |""".stripMargin
  )
  check(
    "fuzzy1",
    """
      |object A {
      |  new PBuil@@
      |}""".stripMargin,
    """|ProcessBuilder java.lang
       |""".stripMargin
  )
  check(
    "companion",
    """
      |object A {
      |  Map@@
      |}""".stripMargin,
    """|Map: scala.collection.immutable.Map.type
       |""".stripMargin
  )
  check(
    "pkg",
    """
      |import scala.collection.conc@@
      |""".stripMargin,
    """|concurrent scala.collection
       |""".stripMargin
  )

  //  The following method tests too many results so we only assert the total number of results
  // to catch at least regressions. It's OK to update the expected number, but at least double check
  // the output makes sense before doing so.
  checkLength(
    "open",
    """
      |object Local {
      |  @@
      |}""".stripMargin,
    441
  )
}
