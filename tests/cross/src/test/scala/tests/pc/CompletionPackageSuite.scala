package tests.pc

import tests.BaseCompletionSuite

object CompletionPackageSuite extends BaseCompletionSuite {
  checkEdit(
    "ident",
    """|package abc@@
       |class Main
       |""".stripMargin,
    """|package abc.def.ghi
       |class Main
       |""".stripMargin,
    filterText = "abc.def.ghi",
    filename = "abc/def/ghi/Main.scala"
  )
  checkEdit(
    "select",
    """|package abc.d@@
       |class Main
       |""".stripMargin,
    """|package abc.def.ghi
       |class Main
       |""".stripMargin,
    filterText = "abc.def.ghi",
    filename = "abc/def/ghi/Main.scala"
  )
}
