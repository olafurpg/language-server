package tests

import scala.sys.process._
import java.nio.file.Files
import java.nio.file.StandardOpenOption

object PantsSlowSuite extends BaseSlowSuite("pants") {
  test("pants") {
    if (!workspace.resolve(".git").isDirectory) {
      Process(
        List(
          "git",
          "clone",
          "https://github.com/cosmicexplorer/pants.git",
          "."
        ),
        Some(workspace.toFile)
      ).!!
    }
    Process(List("git", "pull"), Some(workspace.toFile)).!!
    Process(
      List(
        "./pants",
        "binary",
        "contrib/bloop/src/scala/pants/contrib/bloop/config:bloop-config-gen"
      ),
      Some(workspace.toFile),
      "MODE" -> "debug"
    ).!!
    Files.write(
      workspace.resolve("BUILD.tools").toNIO,
      """
jar_library(
  name = 'bloop-config-gen',
  jars = [
    jar(
      org='org.pantsbuild', name='bloop-config-gen_2.12', rev='???',
      url='file://$(pwd)/dist/bloop-config-gen.jar',
      mutable=True)
  ],
)
""".getBytes(),
      StandardOpenOption.APPEND
    )
  }
}
