package tests

object CascadeSlowSuite extends BaseSlowSuite("cascade") {
  testAsync("basic") {
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{
          |  "a": { },
          |  "b": { "dependsOn": ["a"] }
          |}
          |/a/src/main/scala/a/A.scala
          |package a
          |object A {
          |  val n = 42
          |}
          |/b/src/main/scala/b/B.scala
          |package b
          |object B {
          |  val n: String = a.A.n
          |}
        """.stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/a/A.scala")
      _ = {
        assertNoDiff(
          client.workspaceDiagnostics,
          """|b/src/main/scala/b/B.scala:3:23: error: type mismatch;
             | found   : Int
             | required: String
             |  val n: String = a.A.n
             |                      ^
          """.stripMargin
        )
      }
    } yield ()
  }

  testAsync("fail") {
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{
          |  "a": { },
          |  "b": { "dependsOn": ["a"] }
          |}
          |/a/src/main/scala/a/A.scala
          |package a
          |object A {
          |  val n = 42
          |  val m: String = 42
          |}
          |/b/src/main/scala/b/B.scala
          |package b
          |object B {
          |  val n: String = a.A.n
          |}
        """.stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/a/A.scala")
      _ = {
        assertNoDiff(
          client.workspaceDiagnostics,
          """|a/src/main/scala/a/A.scala:4:19: error: type mismatch;
             | found   : Int(42)
             | required: String
             |  val m: String = 42
             |                  ^^
          """.stripMargin
        )
      }
      _ <- server.didSave("a/src/main/scala/a/A.scala")(
        _.replaceFirst(": String", "")
      )
      _ = {
        assertNoDiff(
          client.workspaceDiagnostics,
          """|b/src/main/scala/b/B.scala:3:23: error: type mismatch;
             | found   : Int
             | required: String
             |  val n: String = a.A.n
             |                      ^
          """.stripMargin
        )
      }
    } yield ()
  }
}
