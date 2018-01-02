package scala.meta.languageserver.protocol

import monix.reactive.Observable
import scodec.DecodeResult
import scodec.bits.BitVector

object ProtocolParser {
  def lspMessageExtractor: Observable[Array[Byte]] => Observable[List[BaseProtocol]] = obsBytes => {
    //Start with am empty decoderesult and pass along decoded messages plus remainder bits as needed
    val emptyDecodeResult: DecodeResult[List[BaseProtocol]] = DecodeResult(List[BaseProtocol](), BitVector.empty)
    obsBytes
      .scan(emptyDecodeResult) { case (DecodeResult(messages, buf), newBytes) =>
        BaseProtocol.listCodec.decode(buf ++ BitVector(newBytes))//Decode if we can
          .getOrElse(emptyDecodeResult.copy(remainder = buf ++ BitVector(newBytes))) //else save bytes for next try
      }.collect { case DecodeResult(messages, _) if messages.nonEmpty => messages } //Only forward nonEmpty lists
  }
}


