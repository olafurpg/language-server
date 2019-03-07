package tests.pc

import tests.BaseCompletionSuite

object CompletionSnippetSuite extends BaseCompletionSuite {
  checkSnippet(
    "member",
    """
      |object Main {
      |  List.appl@@
      |}
      |""".stripMargin,
    """|apply($0)
       |""".stripMargin
  )

  checkSnippet(
    "scope",
    """
      |object Main {
      |  printl@@
      |  
      |}
      |""".stripMargin,
    """|println()
       |println($0)
       |""".stripMargin
  )

  checkSnippet(
    "nullary",
    """
      |object Main {
      |  List(1).hea@@
      |}
      |""".stripMargin,
    """|head
       |headOption
       |""".stripMargin
  )

  checkSnippet(
    "java-nullary",
    """
      |class Foo {
      |  override def toString = "Foo"
      |}
      |object Main {
      |  new Foo().toStrin@@
      |  
      |}
      |""".stripMargin,
    // even if `Foo.toString` is nullary, it overrides `Object.toString()`
    // which is a Java non-nullary method with an empty parameter list.
    """|toString()
       |""".stripMargin
  )

  checkSnippet(
    "java-nullary",
    """
      |class Foo {
      |  override def toString = "Foo"
      |}
      |object Main {
      |  new Foo().toStrin@@
      |  
      |}
      |""".stripMargin,
    // even if `Foo.toString` is nullary, it overrides `Object.toString()`
    // which is a Java non-nullary method with an empty parameter list.
    """|toString()
       |""".stripMargin
  )

  checkSnippet(
    "type",
    s"""|object Main {
        |  val x: scala.IndexedSe@@
        |}
        |""".stripMargin,
    // It's expected to have two separate results, one for `object IndexedSeq` (which should not
    // expand snipppet) and one for `type IndexedSeq[T]`.
    """|IndexedSeq
       |IndexedSeq[$0]
       |""".stripMargin
  )

  checkSnippet(
    "type2",
    s"""|object Main {
        |  new scala.IndexedSeq@@
        |}
        |""".stripMargin,
    """|IndexedSeq
       |IndexedSeq[$0]
       |""".stripMargin
  )

  checkSnippet(
    "type3",
    s"""|object Main {
        |  new ArrayDeque@@
        |}
        |""".stripMargin,
    """|IndexedSeq
       |IndexedSeq[$0]
       |""".stripMargin
  )
}
