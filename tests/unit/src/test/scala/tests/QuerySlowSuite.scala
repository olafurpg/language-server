package tests

object QuerySlowSuite extends BaseSlowSuite("query") {
  testAsync("symbol") {
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{
          |  "a": {
          |    "libraryDependencies": [
          |      "com.typesafe.akka:akka-actor_2.12:2.5.18",
          |      "com.github.finagle:finch-test_2.12:0.26.0",
          |      "org.apache.spark:spark-sql_2.12:2.4.0"
          |    ]
          |  }
          |}
          |/a/src/main/scala/A.scala
          |object A
        """.stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/A.scala")
      _ = pprint.log(server.workspaceSymbol("Server"))
    } yield ()
  }
}
