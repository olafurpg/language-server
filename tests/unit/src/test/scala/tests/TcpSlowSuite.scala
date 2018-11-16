package tests

import scala.meta.internal.metals.BloopProtocol

object TcpSlowSuite extends BaseSlowSuite {
  override def protocol: BloopProtocol = BloopProtocol.Tcp
  testAsync("tcp") {
    for {
      _ <- server.initialize(
        """
          |/project/build.properties
          |sbt.version=1.2.3
          |/build.sbt
          |scalaVersion := "2.12.7"
        """.stripMargin
      )
      _ = assertNoDiff(client.workspaceErrorShowMessages, "")
    } yield ()
  }
}
