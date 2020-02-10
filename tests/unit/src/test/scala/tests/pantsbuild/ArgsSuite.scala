package tests.pantsbuild

import tests.BaseSuite
import scala.meta.internal.pantsbuild._
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

class ArgsSuite extends BaseSuite {
  def checkHelp(args: List[String], expected: String): Unit = {
    test(args.mkString(" ")) {
      val out = new ByteArrayOutputStream()
      val print = new PrintStream(out)
      val app = BloopPants.app.copy(
        out = print,
        err = print
      )
      val exit = app.run(args)
      assertEquals(exit, 0)
      val obtained = out.toString(StandardCharsets.UTF_8.name)
      assertNoDiff(obtained, expected)
    }
  }

  checkHelp(
    List("help"),
    ""
  )

}
