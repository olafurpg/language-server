package scala.meta.languageserver.protocol

import java.io.InputStream

import ScodecUtils.{bracketedBy, lookaheadIssue98Patch}
import monix.reactive.Observable
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{bytes, constant, list, optional}

case class BaseProtocol(mimeType: Option[String], content: ByteVector)
object BaseProtocol {
  val CONTENT_LENGTH_KEY = "Content-Length"
  val CONTENT_TYPE_KEY = "Content-Type"
  val codec: Codec[BaseProtocol] =
    (bracketedBy("Content-Length:", "\r\n").xmap[Int](_.trim.toInt, _.toString)
      ~ optional(lookaheadIssue98Patch(constant('C')), bracketedBy("Content-Type:", "\r\n"))
      ).dropRight(constant(ByteVector('\r', '\n')))
      .consume { case (len, mime) =>
        bytes(len).xmap[BaseProtocol](bytes => BaseProtocol(mime, bytes), lsp => lsp.content)
      }(lsp => (lsp.content.length.toInt, lsp.mimeType))

  val listCodec: Codec[List[BaseProtocol]] = list(codec)
}