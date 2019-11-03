package tests.worksheets
import tests.BaseLspSuite

object WorksheetLspSuite extends BaseLspSuite("worksheet") {
  testAsync("basic") {
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{"a": {}}
          |/a/src/main/scala/foo/Lib.scala
          |package foo
          |object Lib {
          |  def increment(i: Int): Int  =i + 1
          |}
          |/a/src/main/scala/Main.sc
          |println(identity(42))
          |println(foo.Lib.increment(42))
          |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/foo/Lib.scala")
      _ <- server.didOpen("a/src/main/scala/Main.sc")
      completion <- server.completion("a/src/main/scala/Main.sc", "identity@@")
      _ = assertNoDiff(completion, "identity[A](x: A): A")
      completion <- server.completion("a/src/main/scala/Main.sc", "increment@@")
      _ = assertNoDiff(completion, "increment(i: Int): Int")
    } yield ()
  }
}
