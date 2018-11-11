package tests

object DiagnosticsSlowSuite extends BaseSlowSuite {

  testAsync("diagnostics") {
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
