package tests

import org.eclipse.lsp4j.MarkupContent
import scala.collection.JavaConverters._
import org.eclipse.lsp4j.jsonrpc.messages.{Either => JEither}

object SignatureHelpSuite extends BasePCSuite {

  def doc(e: JEither[String, MarkupContent]): String = {
    if (e == null) ""
    else if (e.isLeft) {
      " " + e.getLeft
    } else {
      " " + e.getRight.getValue
    }
  }
  def checkDoc(name: String, code: String, expected: String): Unit = {
    check(name, code, expected, includeDocs = true)
  }
  def check(
      name: String,
      code: String,
      expected: String,
      includeDocs: Boolean = false
  ): Unit = {
    test(name) {
      val code2 = code.replaceAllLiterally("@@", "")
      val offset = code.indexOf("@@")
      if (offset < 0) {
        fail("missing @@")
      }
      val result = pc.signatureHelp("A.scala", code2, offset)
      val out = new StringBuilder()
      if (result != null) {
        result.getSignatures.asScala.zipWithIndex.foreach {
          case (signature, i) =>
            if (includeDocs) {
              val sdoc = doc(signature.getDocumentation)
              if (sdoc.nonEmpty) {
                out.append(sdoc).append("\n")
              }
            }
            out
              .append(signature.getLabel)
              .append("\n")
            if (result.getActiveSignature == i) {
              val param = signature.getParameters.get(result.getActiveParameter)
              val column = signature.getLabel.indexOf(param.getLabel)
              if (column < 0) {
                fail(s"""invalid parameter label
                        |  param.label    : ${param.getLabel}
                        |  signature.label: ${signature.getLabel}
                        |""".stripMargin)
              }
              val documentation = doc(param.getDocumentation)
              val typeSignature =
                if (documentation.startsWith("```scala")) {
                  documentation
                    .stripPrefix("```scala\n")
                    .lines
                    .take(1)
                    .mkString(" ", "", "")
                } else {
                  ""
                }
              val indent = " " * column
              out
                .append(indent)
                .append("^" * param.getLabel.length)
                .append(typeSignature)
                .append("\n")
              signature.getParameters.asScala.foreach { param =>
                val pdoc = doc(param.getDocumentation).trim
                  .stripPrefix("```scala\n")
                  .stripSuffix("\n```")
                if (includeDocs && pdoc.nonEmpty) {
                  out
                    .append("  @param ")
                    .append(param.getLabel.replaceFirst(":.*", ""))
                    .append(" ")
                    .append(pdoc)
                    .append("\n")
                }
              }
            }
        }
      }
      assertNoDiff(out.toString(), expected)
    }
  }
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
  checkDoc(
    "curry",
    """
      |object a {
      |  Option(1).fold("")(_ => @@)
      |}
    """.stripMargin,
    """|fold[B](ifEmpty: => B)(f: A => B): B
       |                       ^^^^^^^^^
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
    """|fold[B](ifEmpty: => B)(f: A => B): B
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
       |foldLeft[B](z: B)(op: (B, A) => B): B
       |                  ^^^^^^^^^^^^^^^
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
  check(
    "erroneous",
    """
      |object a {
      |  Option(1).fold("")(_ => a@@)
      |}
    """.stripMargin,
    """|fold[B](ifEmpty: => B)(f: A => B): B
       |                       ^^^^^^^^^
       |""".stripMargin
  )
  checkDoc(
    "canbuildfrom",
    """
      |object a {
      |  List(1).map(x => @@)
      |}
    """.stripMargin,
    """|map[B, That](f: A => B)(bf: scala.collection.generic.CanBuildFrom[List[A],B,That]): That
       |             ^^^^^^^^^
       |  @param f Int => ???
       |""".stripMargin
  )
  check(
    "canbuildfrom2",
    """
      |object a {
      |  List(1).map(@@)
      |}
    """.stripMargin,
    """|map[B, That](f: A => B)(bf: scala.collection.generic.CanBuildFrom[List[A],B,That]): That
       |             ^^^^^^^^^
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
    """|collect[B](pf: PartialFunction[A,B]): Option[B]
       |           ^^^^^^^^^^^^^^^^^^^^^^^^
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
    """|flatMap[B](f: A => Option[B]): Option[B]
       |           ^^^^^^^^^^^^^^^^^
       |""".stripMargin
  )

  check(
    "curry",
    """
      |object a {
      |  Map[Int](1 @@-> "").map {
      |  }
      |}
    """.stripMargin,
    ""
  )
  // TODO: stress curried arguments with incomplete arg lists like `foo(a, b)(d)` where `c` is missing from
  // `foo(a, b, c)`.
}
