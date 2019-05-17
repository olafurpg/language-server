package tests.debug

import java.net.URI

object MetalsDebugSuite extends MetalsBaseDebugSuite {
  testDebug("launch")(
    layout = s"""/metals.json
                |{ "a": {} }
                |/a/src/main/scala/PrintHelloWorld.scala
                |object PrintHelloWorld {
                |  def main(args: Array[String]): Unit = {
                |    println("Hello, World!")
                |  }
                |}""".stripMargin,
    act = server => {
      server.launch("a/src/main/scala/PrintHelloWorld.scala")
    },
    assert = client => {
      assertEquals(client.output.stderr, "")
      assertEquals(client.output.stdout, "Hello, World!")
    }
  )

  testDebug("working-dir")(
    layout = s"""|/metals.json
                 |{ "a": {} }
                 |
                 |/a/src/main/scala/CreateFileInWorkingDir.scala
                 |import java.nio.file.Paths
                 |
                 |object CreateFileInWorkingDir {
                 |  def main(args: Array[String]): Unit = {
                 |    val workspace = Paths.get("").toAbsolutePath
                 |    println(workspace.toUri)
                 |  }
                 |}""".stripMargin,
    act = server => {
      server.launch("a/src/main/scala/CreateFileInWorkingDir.scala")
    },
    assert = client => {
      val obtained = URI.create(client.output.stdout)
      assertEquals(obtained, workspace)
    }
  )
}
