package tests

import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.duration.Duration
import scala.meta.internal.io.PathIO
import scala.meta.internal.metals.Buffers
import scala.meta.internal.metals.Commands
import scala.meta.internal.metals.Messages._
import scala.meta.internal.metals.MetalsSlowTaskResult
import scala.meta.internal.metals.SbtChecksum
import scala.meta.io.AbsolutePath

object EndToEndSuite extends BaseSuite {
  implicit val ex: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  var server: TestingServer = _
  var client: TestingClient = _
  var workspace: AbsolutePath = _
  override def afterAll(): Unit = {
    if (server != null) {
      server.cancel()
    }
    ex.shutdown()
  }
  override def utestBeforeEach(path: Seq[String]): Unit = {
    if (server != null) {
      server.cancel()
    }
    val name = path.last
    workspace = PathIO.workingDirectory
      .resolve("target")
      .resolve("e2e")
      .resolve(name.replace(' ', '-'))
    Files.createDirectories(workspace.toNIO)
    val buffers = Buffers()
    client = new TestingClient(workspace, buffers)
    server = new TestingServer(client, workspace, buffers)(ex)
  }

  def clean(): Unit = {
    RecursivelyDelete(workspace.resolve(".metals"))
    RecursivelyDelete(workspace.resolve(".bloop"))
  }

  testAsync("import", maxDuration = Duration("3min")) {
    clean()
    for {
      _ <- server.initialize(
        """|/project/build.properties
           |sbt.version=1.2.6
           |/build.sbt
           |scalaVersion := "2.12.7"
           |""".stripMargin
      )
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          // Project has no .bloop directory so user is asked to "import via bloop"
          ImportProjectViaBloop.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
      _ = client.messageRequests.clear() // restart
      _ = assertIsFile(workspace.resolve(SbtChecksum.Md5))
      _ <- server.didChange("build.sbt")(_ + "\n// comment")
      _ = assertNoDiff(client.workspaceMessageRequests, "")
      _ <- server.didSave("build.sbt")(identity)
      // Comment changes do not trigger "re-import project" request
      _ = assertNoDiff(client.workspaceMessageRequests, "")
      _ <- server.didChange("build.sbt") { text =>
        text + "\nversion := \"1.0.0\"\n"
      }
      _ = assertNoDiff(client.workspaceMessageRequests, "")
      _ <- server.didSave("build.sbt")(identity)
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          // Project has .bloop directory so user is asked to "re-import project"
          ReimportSbtProject.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
    } yield ()
  }

  testAsync("command", maxDuration = Duration("3min")) {
    clean()
    for {
      _ <- server.initialize(
        """|/project/build.properties
           |sbt.version=1.2.6
           |/build.sbt
           |scalaVersion := "2.12.7"
           |""".stripMargin
      )
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          // Project has no .bloop directory so user is asked to "import via bloop"
          ImportProjectViaBloop.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
      _ = client.messageRequests.clear() // restart
      _ <- server.executeCommand(Commands.IMPORT_BUILD)
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          BloopInstallProgress.message
        ).mkString("\n")
      )
    } yield ()
  }

  testAsync("progress", maxDuration = Duration("3min")) {
    clean()
    for {
      _ <- server.initialize(
        """|/project/build.properties
           |sbt.version=1.2.6
           |/build.sbt
           |scalaVersion := "2.12.7"
           |""".stripMargin
      )
    } yield ()
  }

  testAsync("reload", maxDuration = Duration("3min")) {
    clean()
    for {
      _ <- server.initialize(
        """|/project/build.properties
           |sbt.version=1.2.6
           |/build.sbt
           |scalaVersion := "2.12.7"
           |/src/main/scala/reload/Main.scala
           |package reload
           |object Main extends App {
           |  println("sourcecode.Line(42)")
           |}
           |""".stripMargin
      )
      _ <- server.didOpen("src/main/scala/reload/Main.scala")
      _ = assertNoDiff(client.workspaceDiagnostics, "")
      _ <- server.didSave("build.sbt") { text =>
        s"""$text
           |libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.1.4"
           |""".stripMargin
      }
      _ <- server
        .didSave("src/main/scala/reload/Main.scala") { text =>
          text.replaceAll("\"", "")
        }
        .recover { case e => scribe.error("compile", e) }
      _ = assertNoDiff(client.workspaceDiagnostics, "")
    } yield ()
  }

  testAsync("import-cancel", maxDuration = Duration("3min")) {
    client.slowTaskHandler = params => {
      if (params == BloopInstallProgress) {
        Thread.sleep(TimeUnit.SECONDS.toMillis(2))
        Some(MetalsSlowTaskResult(cancel = true))
      } else {
        None
      }
    }
    clean()
    for {
      _ <- server.initialize(
        """
          |/project/build.properties
          |sbt.version=1.2.6
          |/build.sbt
          |version := "1.0"
          |""".stripMargin
      )
      _ = assertNotFile(workspace.resolve(SbtChecksum.Md5))
      _ = client.slowTaskHandler = _ => None
      _ <- server.didSave("build.sbt")(identity)
      _ = assertNoDiff(client.workspaceShowMessages, "")
      _ = assertIsFile(workspace.resolve(SbtChecksum.Md5))
    } yield ()
  }

  testAsync("definition", maxDuration = Duration("3min")) {
    for {
      _ <- server.initialize(
        """|
           |/project/build.properties
           |sbt.version=1.2.6
           |/build.sbt
           |scalaVersion := "2.12.7"
           |libraryDependencies ++= List(
           |  "org.scalatest" %% "scalatest" % "3.0.5" % Test
           |)
           |/src/main/java/a/Message.java
           |package a;
           |public class Message {
           |  public static String message = "Hello world!";
           |}
           |/src/main/scala/a/Main.scala
           |package a
           |import java.util.concurrent.Future // unused
           |import scala.util.Failure // unused
           |object Main extends App {
           |  val message = Message.message
           |  new java.io.PrintStream(new java.io.ByteArrayOutputStream())
           |  println(message)
           |}
           |/src/test/scala/a/MainSuite.scala
           |package a
           |import java.util.concurrent.Future // unused
           |import scala.util.Failure // unused
           |import org.scalatest.FunSuite
           |object MainSuite extends FunSuite {
           |  test("a") {
           |    val condition = Main.message.contains("Hello")
           |    assert(condition)
           |  }
           |}
           |""".stripMargin
      )
      _ = assertNoDiff(server.workspaceDefinitions, "")
      _ <- server.didOpen("src/main/scala/a/Main.scala")
      _ <- server.didOpen("src/test/scala/a/MainSuite.scala")
      _ = assertNoDiff(
        server.workspaceDefinitions,
        """|
           |/src/main/scala/a/Main.scala
           |package a
           |import java.util.concurrent.Future/*Future.java:95*/ // unused
           |import scala.util.Failure/*Try.scala:213*/ // unused
           |object Main/*L3*/ extends App/*App.scala:38*/ {
           |  val message/*L4*/ = Message/*Message.java:1*/.message/*Message.java:2*/
           |  new java.io.PrintStream/*PrintStream.java:56*/(new java.io.ByteArrayOutputStream/*ByteArrayOutputStream.java:44*/())
           |  println/*Predef.scala:392*/(message/*L4*/)
           |}
           |/src/test/scala/a/MainSuite.scala
           |package a
           |import java.util.concurrent.Future/*Future.java:95*/ // unused
           |import scala.util.Failure/*Try.scala:213*/ // unused
           |import org.scalatest.FunSuite/*FunSuite.scala:1559*/
           |object MainSuite/*L4*/ extends FunSuite/*FunSuite.scala:1559*/ {
           |  test/*FunSuiteLike.scala:119*/("a") {
           |    val condition/*L6*/ = Main/*Main.scala:3*/.message/*Main.scala:4*/.contains/*String.java:2131*/("Hello")
           |    assert/*<no symbol>*/(condition/*L6*/)
           |  }
           |}
           |""".stripMargin
      )
      _ <- server.didChange("src/test/scala/a/MainSuite.scala") { text =>
        ">>>>>>>\n\n" + text.replaceFirst("\"a\"", "testName")
      }
      _ <- server.didChange("src/main/scala/a/Main.scala") { text =>
        ">>>>>>>\n\n" + text.replaceFirst("message", "helloMessage")
      }
      _ = assertNoDiff(
        // Check that:
        // - navigation works for all unchanged identifiers, even if the buffer doesn't parse
        // - line numbers have shifted by 2 for both local and Main.scala references in MainSuite.scala
        // - old references to `message` don't resolve because it has been renamed to `helloMessage`
        // - new references to like `testName` don't resolve
        server.workspaceDefinitions,
        """|
           |/src/main/scala/a/Main.scala
           |>>>>>>>/*<no symbol>*/
           |
           |package a
           |import java.util.concurrent.Future/*Future.java:95*/ // unused
           |import scala.util.Failure/*Try.scala:213*/ // unused
           |object Main/*L5*/ extends App/*App.scala:38*/ {
           |  val helloMessage/*<no symbol>*/ = Message/*Message.java:1*/.message/*Message.java:2*/
           |  new java.io.PrintStream/*PrintStream.java:56*/(new java.io.ByteArrayOutputStream/*ByteArrayOutputStream.java:44*/())
           |  println/*Predef.scala:392*/(message/*<no symbol>*/)
           |}
           |/src/test/scala/a/MainSuite.scala
           |>>>>>>>/*<no symbol>*/
           |
           |package a
           |import java.util.concurrent.Future/*Future.java:95*/ // unused
           |import scala.util.Failure/*Try.scala:213*/ // unused
           |import org.scalatest.FunSuite/*FunSuite.scala:1559*/
           |object MainSuite/*L6*/ extends FunSuite/*FunSuite.scala:1559*/ {
           |  test/*FunSuiteLike.scala:119*/(testName/*<no symbol>*/) {
           |    val condition/*L8*/ = Main/*Main.scala:5*/.message/*<no symbol>*/.contains/*String.java:2131*/("Hello")
           |    assert/*<no symbol>*/(condition/*L8*/)
           |  }
           |}
           |""".stripMargin
      )
    } yield ()
  }

  // TODO: 2.12 dependency navigation OK
  // TODO: 2.11 dependency navigation Warning

  testAsync("install-error", maxDuration = Duration("3min")) {
    clean()
    for {
      _ <- server.initialize(
        """|/project/build.properties
           |sbt.version=1.2.6
           |/build.sbt
           |, syntax error
           |""".stripMargin
      )
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          ImportProjectViaBloop.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
      _ = assertNoDiff(
        client.workspaceShowMessages,
        ImportProjectFailed.getMessage
      )
      _ = assertNotFile(workspace.resolve(SbtChecksum.Md5))
      _ = client.messageRequests.clear()
      _ <- server.didSave("build.sbt") { _ =>
        """scalaVersion := "2.12.7" """
      }
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          ImportProjectViaBloop.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
    } yield ()
  }

  testAsync("diagnostics", maxDuration = Duration("3min")) {
    for {
      _ <- server.initialize(
        """|/project/build.properties
           |sbt.version=1.2.6
           |/build.sbt
           |scalaVersion := "2.12.7"
           |scalacOptions ++= List(
           |  "-Yrangepos",
           |  "-Ywarn-unused"
           |)
           |/src/main/scala/a/Example.scala
           |package a
           |import java.util.concurrent.Future // unused
           |import scala.util.Failure // unused
           |class Example
           |/src/main/scala/a/Main.scala
           |package a
           |import java.util.concurrent.Future // unused
           |import scala.util.Failure // unused
           |class Main
           |/src/test/scala/a/MainSuite.scala
           |package a
           |import java.util.concurrent.Future // unused
           |import scala.util.Failure // unused
           |class MainSuite
           |""".stripMargin
      )
      _ = assertNoDiff(client.workspaceDiagnostics, "")
      _ <- server.didOpen("src/main/scala/a/Main.scala")
      // NOTE(olafur): can only test warnings until bloop upgrades to BSP v2
      _ = assertNoDiff(
        client.workspaceDiagnostics,
        """|
           |src/main/scala/a/Example.scala:2:1: warning: Unused import
           |import java.util.concurrent.Future // unused
           |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
           |src/main/scala/a/Example.scala:3:1: warning: Unused import
           |import scala.util.Failure // unused
           |^^^^^^^^^^^^^^^^^^^^^^^^^
           |src/main/scala/a/Main.scala:2:1: warning: Unused import
           |import java.util.concurrent.Future // unused
           |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
           |src/main/scala/a/Main.scala:3:1: warning: Unused import
           |import scala.util.Failure // unused
           |^^^^^^^^^^^^^^^^^^^^^^^^^
           |""".stripMargin
      )
      _ <- server.didOpen("src/test/scala/a/MainSuite.scala")
      _ = assertNoDiff(
        client.workspaceDiagnostics,
        """
          |src/main/scala/a/Example.scala:2:1: warning: Unused import
          |import java.util.concurrent.Future // unused
          |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
          |src/main/scala/a/Example.scala:3:1: warning: Unused import
          |import scala.util.Failure // unused
          |^^^^^^^^^^^^^^^^^^^^^^^^^
          |src/main/scala/a/Main.scala:2:1: warning: Unused import
          |import java.util.concurrent.Future // unused
          |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
          |src/main/scala/a/Main.scala:3:1: warning: Unused import
          |import scala.util.Failure // unused
          |^^^^^^^^^^^^^^^^^^^^^^^^^
          |src/test/scala/a/MainSuite.scala:2:1: warning: Unused import
          |import java.util.concurrent.Future // unused
          |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
          |src/test/scala/a/MainSuite.scala:3:1: warning: Unused import
          |import scala.util.Failure // unused
          |^^^^^^^^^^^^^^^^^^^^^^^^^
        """.stripMargin
      )
      _ <- server.didSave("src/test/scala/a/MainSuite.scala")(
        _.lines.filterNot(_.startsWith("import")).mkString("\n")
      )
      _ = assertNoDiff(
        client.workspaceDiagnostics,
        """|
           |src/main/scala/a/Example.scala:2:1: warning: Unused import
           |import java.util.concurrent.Future // unused
           |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
           |src/main/scala/a/Example.scala:3:1: warning: Unused import
           |import scala.util.Failure // unused
           |^^^^^^^^^^^^^^^^^^^^^^^^^
           |src/main/scala/a/Main.scala:2:1: warning: Unused import
           |import java.util.concurrent.Future // unused
           |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
           |src/main/scala/a/Main.scala:3:1: warning: Unused import
           |import scala.util.Failure // unused
           |^^^^^^^^^^^^^^^^^^^^^^^^^
           |""".stripMargin
      )
      _ <- server.didSave("src/main/scala/a/Main.scala")(
        _.lines.filterNot(_.startsWith("import")).mkString("\n")
      )
      // FIXME: https://github.com/scalacenter/bloop/issues/696
      // _ = assertNoDiff(client.workspaceDiagnostics,
      //   """
      //     |src/main/scala/a/Example.scala:2:1: warning: Unused import
      //     |import java.util.concurrent.Future // unused
      //     |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      //     |src/main/scala/a/Example.scala:3:1: warning: Unused import
      //     |import scala.util.Failure // unused
      //     |^^^^^^^^^^^^^^^^^^^^^^^^^
      //   """.stripMargin)
    } yield ()
  }

}
