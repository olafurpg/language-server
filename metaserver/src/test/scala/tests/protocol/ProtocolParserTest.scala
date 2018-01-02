package tests.protocol

import java.nio.charset.StandardCharsets.US_ASCII

import scala.meta.languageserver.protocol.BaseProtocol
import scala.meta.languageserver.protocol.BaseProtocol._
import monix.execution.Scheduler
import monix.reactive.Observable
import scodec.DecodeResult
import scodec.bits._
import tests.MegaSuite


import scala.concurrent.Await
import scala.concurrent.duration._

object ProtocolParserTest extends MegaSuite {
  implicit lazy val scheduler = Scheduler.singleThread("test")

  val bodyString = """{"jsonrpc":"2.0","method":"textDocument/hover","params":{"textDocument":{"uri":"file:///Users/tutysara/src/myprojects/java/BroadleafCommerce/common/src/main/java/test.java"},"position":{"line":2,"character":7}},"id":17}"""
  val example = s"""$CONTENT_LENGTH_KEY: ${bodyString.length}
                  |
                  |$bodyString""".stripMargin.replaceAll("\n", "\r\n")
  val exampleWithMime = s"""$CONTENT_LENGTH_KEY:${bodyString.length}
                  |Content-Type:Json-Rpc
                  |
                  |$bodyString""".stripMargin.replaceAll("\n", "\r\n")
  val expect = BaseProtocol(None, ByteVector(bodyString.getBytes(US_ASCII)))

  def lspMessageExtractor: Observable[Array[Byte]] => Observable[List[BaseProtocol]] = obsBytes => {
    val emptyDecodeResult: DecodeResult[List[BaseProtocol]] = DecodeResult(List[BaseProtocol](), BitVector.empty)
      obsBytes
        .scan(emptyDecodeResult) { case (DecodeResult(messages, buf), newBytes) =>
          listCodec.decode(buf ++ BitVector(newBytes))
            .getOrElse(emptyDecodeResult.copy(remainder = buf ++ BitVector(newBytes)))
        }.collect { case DecodeResult(messages, _) if messages.nonEmpty => messages }
  }

  test("parses whole message from byteStream") {
    val stream = lspMessageExtractor(Observable(example.getBytes(US_ASCII)))
    val result = drain(stream).flatten
    assert(result.map(_.toString) == List(expect.toString))
  }

  test("parses whole message even when input is chunked") {
    val chunks = example.getBytes(US_ASCII).grouped(10).toList
    val stream = lspMessageExtractor(Observable(chunks: _*))
    val result = drain(stream).flatten
    assert(result.map(_.toString) == List(expect.toString))
  }

  def drain[A](input: Observable[A]): List[A] = {
    Await.result(input.toListL.runAsync, 3.seconds)
  }

}


