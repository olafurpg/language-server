package tests.pc

import tests.BaseCompletionSuite

object CompletionDocSuite extends BaseCompletionSuite {
  check(
    "java",
    """
      |object A {
      |  "".substrin@@
      |}
    """.stripMargin,
    """|substring(beginIndex: Int): String
       |substring(beginIndex: Int, endIndex: Int): String
       |""".stripMargin
  )
}
