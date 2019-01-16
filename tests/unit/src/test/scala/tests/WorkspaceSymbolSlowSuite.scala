package tests

import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceSymbolParams
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._

object WorkspaceSymbolSlowSuite extends BaseSlowSuite("workspace-symbol") {
  testAsync("basic") {
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{
          |  "a": {}
          |}
          |/a/src/main/scala/a/b/A.scala
          |package a
          |package b
          |
          |object Foobar {
          |  class Inner
          |}
          |object PazQux {
          |  class Outer {
          |    def bar = {
          |      val x = 1
          |    }
          |  }
          |}
          |/a/src/main/scala/a/B.scala
          |package a
          |class B
          |""".stripMargin
      )
      _ = assertNoDiff(
        server.workspaceSymbol("Paz.Outer"),
        "a.b.PazQux.Outer"
      )
      _ = assertNoDiff(
        server.workspaceSymbol("PQ"),
        "a.b.PazQux"
      )
      _ = assertNoDiff(
        server.workspaceSymbol("b.P"),
        "a.b.PazQux"
      )
      _ = assertNoDiff(
        server.workspaceSymbol("a.b.P"),
        "a.b.PazQux"
      )
      _ = assertNoDiff(
        server.workspaceSymbol("a.P"),
        ""
      )
      _ <- server.didSave("a/src/main/scala/a/B.scala")(
        _.replaceAllLiterally("class B", "  class Haddock")
      )
      _ = assertNoDiff(
        server.workspaceSymbol("Had"),
        "a.Haddock"
      )
    } yield ()
  }
  testAsync("pre-initialized") {
    var request = Future.successful[List[List[SymbolInformation]]](Nil)
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{
          |  "a": {}
          |}
          |/a/src/main/scala/a/b/A.scala
          |package a
          |package b
          |
          |object Foobar {
          |  class Inner
          |}
          |""".stripMargin,
        preInitialized = { () =>
          request = Future
            .sequence(1.to(10).map { _ =>
              server.server
                .workspaceSymbol(new WorkspaceSymbolParams("In"))
                .asScala
                .map(_.asScala.toList)
            })
            .map(_.toList)
          Thread.sleep(10)
          Future.successful(())
        }
      )
      results <- request
      _ = {
        val obtained = results.distinct
        // Assert that all results are the same, makesure we don't return empty/incomplete results
        // before indexing is complete.
        assert(obtained.length == 1)
        assert(obtained.head.head.getName == "Inner")
        assert(obtained.head.head.getContainerName == "a.b.Foobar.")
      }
    } yield ()
  }

  testAsync("classpath") {
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{
          |  "a": { }
          |}
          |""".stripMargin
      )
      query = "Str"
      _ = pprint.log(server.server.workspaceSymbol(query))
      _ = pprint.log(server.server.workspaceSymbol(query))
      _ = pprint.log(server.server.workspaceSymbol(query))
      _ = pprint.log(server.server.workspaceSymbol(query))
    } yield ()
  }
}
