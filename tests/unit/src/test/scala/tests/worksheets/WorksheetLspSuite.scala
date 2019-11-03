package tests.worksheets
import tests.BaseLspSuite
import scala.meta.internal.metals.ClientExperimentalCapabilities
import scala.meta.internal.metals.UserConfiguration

object WorksheetLspSuite extends BaseLspSuite("worksheet") {
  override def experimentalCapabilities
      : Option[ClientExperimentalCapabilities] =
    Some(ClientExperimentalCapabilities(decorationProvider = true))
  override def userConfig: UserConfiguration =
    super.userConfig.copy(screenWidth = 40)
  testAsync("basic") {
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{"a": {}}
          |/a/src/main/scala/foo/Lib.scala
          |package foo
          |object Lib {
          |  def increment(i: Int): Int = i + 1
          |}
          |/a/src/main/scala/Main.sc
          |val x = identity(42)
          |println(foo.Lib.increment(x))
          |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/foo/Lib.scala")
      _ <- server.didOpen("a/src/main/scala/Main.sc")
      completion <- server.completion("a/src/main/scala/Main.sc", "identity@@")
      _ = assertNoDiff(completion, "identity[A](x: A): A")
      completion <- server.completion("a/src/main/scala/Main.sc", "increment@@")
      _ = assertNoDiff(completion, "increment(i: Int): Int")
      _ = assertNoDiff(
        client.workspaceDecorations,
        """|val x = identity(42) // 42
           |println(foo.Lib.increment(x)) 43
           |""".stripMargin
      )
    } yield ()
  }
  testAsync("decoration") {
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{"a": {}}
          |/a/src/main/scala/Main.sc
          |import java.nio.file.Files
          |val name = "Susan"
          |val greeting = s"Hello $name"
          |println(greeting + "\nHow are you?")
          |1.to(10).toVector
          |val List(a, b) = List(42, 10)
          |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/Main.sc")
      _ = assertNoDiff(
        client.workspaceDecorations,
        """|
           |import java.nio.file.Files
           |val name = "Susan" // "Susan"
           |val greeting = s"Hello $name" // "Hello Susan"
           |println(greeting + "\nHow are you?") // Hello Susan
           |1.to(10).toVector // Vector(1,2,3,4,5,6,7,8,
           |val List(a, b) = List(42, 10) // a=42, b=10
           |""".stripMargin
      )
      _ = assertNoDiff(
        client.workspaceDecorationHoverMessage,
        """|import java.nio.file.Files
           |val name = "Susan"
           |name: String = "Susan"
           |val greeting = s"Hello $name"
           |greeting: String = "Hello Susan"
           |println(greeting + "\nHow are you?")
           |// Hello Susan
           |// How are you?
           |1.to(10).toVector
           |res1: Vector[Int] = Vector(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
           |val List(a, b) = List(42, 10)
           |a: Int = 42
           |b: Int = 10
           |""".stripMargin
      )
    } yield ()
  }
}
