package scala.meta.internal.metals

import io.undertow.Handlers.path
import io.undertow.Handlers.websocket
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core.AbstractReceiveListener
import io.undertow.websockets.core.BufferedTextMessage
import io.undertow.websockets.core.StreamSourceFrameChannel
import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.core.WebSockets
import io.undertow.websockets.spi.WebSocketHttpExchange
import java.nio.charset.StandardCharsets
import java.util.Collections
import org.eclipse.lsp4j.ExecuteCommandParams
import scala.collection.mutable
import scala.meta.internal.io.InputStreamIO
import MetalsEnrichments._
import io.undertow.util.StatusCodes

final class MetalsHttpServer private (
    languageServer: MetalsLanguageServer,
    server: Undertow,
    url: String,
    openChannels: mutable.Set[WebSocketChannel]
) {
  def start(): Unit = {
    scribe.info(s"Started Metals http server at $url")
    server.start()
  }
  def stop(): Unit = {
    server.stop()
  }
  def reload(): Unit = {
    pprint.log(s"reload: $openChannels")
    sendJson(s"""{"command":"reload","path":"index.html","liveCss":true}""")
  }
  def alert(message: String): Unit = {
    sendJson(s"""{"command":"alert","message":"$message"}""")
  }
  private def sendJson(json: String): Unit = {
    openChannels.foreach(channel => WebSockets.sendTextBlocking(json, channel))
  }
}

object MetalsHttpServer {

  /**
   * Instantiate an undertow file server that speaks the LiveReload protocol.
   *
   * See LiveReload protocol for more details: http://livereload.com/api/protocol/
   *
   * @param host the hostname of the server.
   * @param port the port of the server.
   */
  def apply(
      host: String = "localhost",
      port: Int = 4000,
      languageServer: MetalsLanguageServer,
      render: () => String
  ): MetalsHttpServer = {
    val url = s"http://$host:$port"
    val openChannels = mutable.Set.empty[WebSocketChannel]
    val baseHandler =
      path()
        .addExactPath("/livereload.js", staticResource("/livereload.js"))
        .addPrefixPath(
          "/execute-command",
          new HttpHandler {
            override def handleRequest(exchange: HttpServerExchange): Unit = {
              val command = for {
                params <- Option(exchange.getQueryParameters.get("command"))
                command <- params.asScala.headOption
              } yield command
              languageServer.executeCommand(
                new ExecuteCommandParams(
                  command.getOrElse("<unknown command>"),
                  Collections.emptyList()
                )
              )
              exchange.setStatusCode(StatusCodes.SEE_OTHER)
              exchange.getResponseHeaders.put(Headers.LOCATION, "/")
              exchange.endExchange()
            }
          }
        )
        .addPrefixPath(
          "/livereload",
          websocket(new LiveReloadConnectionCallback(openChannels))
        )
        .addExactPath(
          "/",
          new HttpHandler {
            override def handleRequest(exchange: HttpServerExchange): Unit = {
              val html = render()
              exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/html")
              exchange.getResponseSender.send(html)
            }
          }
        )
    val httpServer = Undertow.builder
      .addHttpListener(port, host)
      .setHandler(baseHandler)
      .build()
    new MetalsHttpServer(languageServer, httpServer, url, openChannels)
  }

  private def staticResource(path: String): HttpHandler = {
    val is = this.getClass.getResourceAsStream(path)
    if (is == null) throw new NoSuchElementException(path)
    val bytes =
      try InputStreamIO.readBytes(is)
      finally is.close()
    val text = new String(bytes, StandardCharsets.UTF_8)
    new HttpHandler {
      override def handleRequest(exchange: HttpServerExchange): Unit = {
        exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, contentType(path))
        exchange.getResponseSender.send(text)
      }
    }
  }

  private def contentType(path: String): String = {
    if (path.endsWith(".js")) "application/javascript"
    else if (path.endsWith(".css")) "text/css"
    else if (path.endsWith(".html")) "text/html"
    else ""
  }

  private final class LiveReloadConnectionCallback(
      openChannels: mutable.Set[WebSocketChannel]
  ) extends WebSocketConnectionCallback {
    override def onConnect(
        exchange: WebSocketHttpExchange,
        channel: WebSocketChannel
    ): Unit = {
      channel.getReceiveSetter.set(new AbstractReceiveListener() {
        override def onClose(
            webSocketChannel: WebSocketChannel,
            channel: StreamSourceFrameChannel
        ): Unit = {
          openChannels.remove(webSocketChannel)
          super.onClose(webSocketChannel, channel)
        }
        override protected def onFullTextMessage(
            channel: WebSocketChannel,
            message: BufferedTextMessage
        ): Unit = {
          if (message.getData.contains("""command":"hello""")) {
            val hello =
              """{"command":"hello","protocols":["http://livereload.com/protocols/official-7"],"serverName":"mdoc"}"""
            WebSockets.sendTextBlocking(hello, channel)
            openChannels.add(channel)
          }
        }
      })
      channel.resumeReceives()
    }
  }
}
