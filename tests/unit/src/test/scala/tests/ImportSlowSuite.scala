package tests

import java.util.concurrent.TimeUnit
import scala.meta.internal.metals.Messages._
import scala.meta.internal.metals.MetalsSlowTaskResult
import scala.meta.internal.metals.SbtChecksum
import scala.meta.internal.metals.ServerCommands

object ImportSlowSuite extends BaseSlowSuite {

  def currentChecksum: String =
    SbtChecksum.current(workspace).getOrElse {
      fail("no sbt checksum for workspace")
    }
  def assertNoStatus(): Unit =
    server.server.tables.sbtChecksums.getStatus(currentChecksum) match {
      case Some(value) =>
        fail(s"expected no status. obtained $value", stackBump = 1)
      case None =>
        () // OK
    }
  def assertStatus(fn: SbtChecksum.Status => Boolean): Unit = {
    val checksum = currentChecksum
    server.server.tables.sbtChecksums.getStatus(checksum) match {
      case Some(status) =>
        assert(fn(status))
      case None =>
        fail(s"missing persisted checksum $checksum", stackBump = 1)
    }
  }
  testAsync("basic") {
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
          ImportBuildViaBloop.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
      _ = client.messageRequests.clear() // restart
      _ = assertStatus(_.isInstalled)
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

  testAsync("force-command") {
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
          ImportBuildViaBloop.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
      _ = client.messageRequests.clear() // restart
      _ <- server.executeCommand(ServerCommands.ImportBuild)
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          BloopInstallProgress.message
        ).mkString("\n")
      )
    } yield ()
  }

  testAsync("new-dependency") {
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

  testAsync("cancel") {
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
      _ = assertStatus(_.isCancelled)
      _ = client.slowTaskHandler = _ => None
      _ <- server.didSave("build.sbt")(identity)
      _ = assertNoDiff(client.workspaceShowMessages, "")
      _ = assertStatus(_.isInstalled)
    } yield ()
  }

  testAsync("error") {
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
          ImportBuildViaBloop.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
      _ = assertNoDiff(
        client.workspaceShowMessages,
        ImportProjectFailed.getMessage
      )
      _ = assertStatus(_.isFailed)
      _ = client.messageRequests.clear()
      _ <- server.didSave("build.sbt") { _ =>
        """scalaVersion := "2.12.7" """
      }
      _ = assertNoDiff(
        client.workspaceMessageRequests,
        List(
          ImportBuildViaBloop.params.getMessage,
          BloopInstallProgress.message
        ).mkString("\n")
      )
    } yield ()
  }

}
