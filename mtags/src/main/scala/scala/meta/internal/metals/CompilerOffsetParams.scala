package scala.meta.internal.metals

import java.net.URI
import scala.meta.inputs.Position
import scala.meta.pc.CancelToken
import scala.meta.pc.OffsetParams
import scala.meta.internal.inputs.XtensionInputSyntaxStructure

case class CompilerOffsetParams(
    uri: URI,
    text: String,
    offset: Int,
    token: CancelToken = EmptyCancelToken
) extends OffsetParams

object CompilerOffsetParams {

  def fromPos(pos: Position, token: CancelToken): CompilerOffsetParams = {
    CompilerOffsetParams(
      URI.create(pos.input.syntax),
      pos.input.text,
      pos.start,
      token
    )
  }
}
