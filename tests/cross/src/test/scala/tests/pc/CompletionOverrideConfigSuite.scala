package tests.pc

import scala.meta.internal.pc.PresentationCompilerConfigImpl
import scala.meta.pc.PresentationCompilerConfig
import tests.BaseCompletionSuite

object CompletionOverrideConfigSuite extends BaseCompletionSuite {

  override def config: PresentationCompilerConfig =
    PresentationCompilerConfigImpl().copy(
      _symbolPrefixes = Map(
        "a/Weekday." -> "w"
      )
    )
  checkEditLine(
    "object",
    """|package a
       |object Weekday {
       |  case class Monday()
       |}
       |class Super {
       |  def weekday: Weekday.Monday
       |}
       |class Main extends Super {
       |___
       |}
       |""".stripMargin,
    "  def weekday@@",
    """  import a.{Weekday => w}
      |  def weekday: w.Monday = ${0:???}""".stripMargin
  )
}
