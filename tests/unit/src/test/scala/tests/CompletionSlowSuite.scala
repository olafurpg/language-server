package tests

import scala.concurrent.Future

object CompletionSlowSuite extends BaseSlowSuite("completion") {

  def assertCompletion(
      query: String,
      expected: String,
      project: Char = 'a'
  )(implicit file: sourcecode.File, line: sourcecode.Line): Future[Unit] = {
    val filename = s"$project/src/main/scala/$project/${project.toUpper}.scala"
    server.completion(filename, query).map { completion =>
      assertNoDiff(completion, expected)
    }
  }

  testAsync("basic") {
    for {
      _ <- server.initialize(
        """/metals.json
          |{
          |  "a": {}
          |}
          |/a/src/main/scala/a/A.scala
          |package a
          |object A {
          |  val x = "".substrin
          |  Stream
          |  TrieMap
          |  locally {
          |    val myLocalVariable = Array("")
          |    myLocalVariable
          |    val source = ""
          |  }
          |}
          |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/a/A.scala")
      _ = assertNotEmpty(client.workspaceDiagnostics)
      _ <- assertCompletion(
        "substrin@@",
        """|substring(beginIndex: Int): String
           |substring(beginIndex: Int, endIndex: Int): String
           |""".stripMargin
      )
      _ <- assertCompletion(
        "Stream@@",
        """|Stream scala.collection.immutable
           |Stream java.util.stream
           |IntStream java.util.stream
           |LogStream java.rmi.server
           |BaseStream java.util.stream
           |LongStream java.util.stream
           |StreamView scala.collection.immutable
           |InputStream java.io
           |PrintStream java.io
           |DoubleStream java.util.stream
           |OutputStream java.io
           |StreamBuilder scala.collection.immutable.Stream
           |StreamHandler java.util.logging
           |StreamCanBuildFrom scala.collection.immutable.Stream
           |""".stripMargin
      )
      _ <- assertCompletion(
        "TrieMap@@",
        """|TrieMap scala.collection.concurrent
           |ParTrieMap scala.collection.parallel.mutable
           |HashTrieMap scala.collection.immutable.HashMap
           |ParTrieMapCombiner scala.collection.parallel.mutable
           |ParTrieMapSplitter scala.collection.parallel.mutable
           |TrieMapSerializationEnd scala.collection.concurrent
           |""".stripMargin
      )
      _ <- assertCompletion(
        "  myLocalVariable@@",
        """|myLocalVariable: Array[String]
           |""".stripMargin
      )
    } yield ()
  }

  testAsync("workspace") {
    cleanWorkspace()
    for {
      _ <- server.initialize(
        """/metals.json
          |{
          |  "b": {},
          |  "c": {},
          |  "a": { "dependsOn": ["c"] }
          |}
          |/b/src/main/scala/b/DefinedInB.scala
          |package b
          |object DefinedInB {
          |}
          |/c/src/main/scala/c/DefinedInC.scala
          |package c
          |object DefinedInC {
          |}
          |/a/src/main/scala/a/DefinedInA.scala
          |package a
          |object Outer {
          |  class DefinedInA
          |}
          |/a/src/main/scala/a/A.scala
          |package a
          |object Main {
          |  // DefinedIn
          |}
          |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/a/A.scala")
      _ = assertEmpty(client.workspaceDiagnostics)
      _ <- server.didChange("a/src/main/scala/a/A.scala")(
        _.replaceAllLiterally("// ", "")
      )
      // assert that "DefinedInB" does not appear in results
      _ <- assertCompletion(
        "DefinedIn@@",
        """|DefinedInA a.Outer
           |DefinedInC c
           |""".stripMargin
      )
    } yield ()
  }
}
