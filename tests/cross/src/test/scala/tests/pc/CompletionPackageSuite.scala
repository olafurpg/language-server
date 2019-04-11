package tests.pc

import tests.BaseCompletionSuite

object CompletionPackageSuite extends BaseCompletionSuite {
  check(
    "class",
    """|package a@@
       |class MMain
       |""".stripMargin,
    "package abc.def",
    filename = "abc/def/Main.scala"
  )
}
