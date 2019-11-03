package scala.meta.internal.metals

import scala.meta.pc.CancelToken
import scala.meta.pc.OffsetParams
import scala.meta.inputs.Position

case class CompilerOffsetParams(
    filename: String,
    text: String,
    offset: Int,
    token: CancelToken = EmptyCancelToken
) extends OffsetParams

object CompilerOffsetParams {
  def fromPos(pos: Position, token: CancelToken): CompilerOffsetParams = {
    val isScript = pos.input.syntax.endsWith(".sc")
    val scriptHeader = "object Script {\n"
    val text =
      if (isScript) scriptHeader + pos.input.text + "\n}"
      else pos.input.text
    val start =
      if (isScript) scriptHeader.length + pos.start
      else pos.start
    CompilerOffsetParams(
      pos.input.syntax,
      text,
      start,
      token
    )
  }
}
