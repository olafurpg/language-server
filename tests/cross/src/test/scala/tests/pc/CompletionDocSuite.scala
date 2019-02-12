package tests.pc

import tests.BaseCompletionSuite

object CompletionDocSuite extends BaseCompletionSuite {

  check(
    "java",
    """
      |object A {
      |  "".substrin@@
      |}
    """.stripMargin,
    """|substring(beginIndex: Int): String
       |substring(beginIndex: Int, endIndex: Int): String
       |""".stripMargin
  )

  check(
    "java2",
    """
      |object A {
      |  String.join@@
      |}
    """.stripMargin,
    """|join(delimiter: CharSequence, elements: CharSequence*): String
       |join(delimiter: CharSequence, elements: Iterable[_ <: CharSequence]): String
       |""".stripMargin
  )

  check(
    "java3",
    """
      |import scala.collection.JavaConverters._
      |object A {
      |  new java.util.HashMap[String, Int]().entrySet.asScala.foreach { entry =>
      |    entry.setV@@
      |  }
      |}
    """.stripMargin,
    """|setValue(value: Int): Int
       |""".stripMargin
  )
  check(
    "java4",
    """
      |object A {
      |  java.util.Collections.singletonLis@@
      |}
    """.stripMargin,
    """|singletonList[T](o: T): List[T]
       |""".stripMargin
  )
  check(
    "scala",
    """
      |object A {
      |  val source: io.Source = ???
      |  source.reportWarn@@
      |}
    """.stripMargin,
    """|reportWarning(pos: Int, msg: String, out: PrintStream = Console.out): Unit
       |""".stripMargin
  )

  check(
    "scala1",
    """
      |object A {
      |  List(1).iterator.sliding@@
      |}
    """.stripMargin,
    """|sliding[B >: Int](size: Int, step: Int = 1): Iterator[Int]#GroupedIterator[B]
       |""".stripMargin
  )

  check(
    "scala2",
    """
      |object A {
      |  println@@
      |}
    """.stripMargin,
    """|Prints a newline character on the default output.
       |println(): Unit
       |Prints out an object to the default output, followed by a newline character.
       |println(x: Any): Unit
       |""".stripMargin,
    includeDocs = true
  )

  check(
    "local",
    """
      |object A {
      |  locally {
      |    val myNumbers = Vector(1)
      |    myNumbers@@
      |  }
      |}
    """.stripMargin,
    """|myNumbers: Vector[Int]
       |""".stripMargin
  )
}
