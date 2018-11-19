package scala.meta.internal.metals

import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import scala.meta.io.AbsolutePath

final class MetalsHttpClient(
    workspace: AbsolutePath,
    url: String,
    underlying: MetalsLanguageClient,
    triggerReload: () => Unit,
    charset: Charset,
    icons: Icons
) extends MetalsLanguageClient {
  val status = new AtomicReference[String]("")
  override def metalsStatus(params: MetalsStatusParams): Unit = {
    if (params.hide) status.set("")
    else status.set(params.text)
    triggerReload()
    underlying.metalsStatus(params)
  }
  override def metalsSlowTask(
      params: MetalsSlowTaskParams
  ): CompletableFuture[MetalsSlowTaskResult] =
    underlying.metalsSlowTask(params)
  override def telemetryEvent(value: Any): Unit =
    underlying.telemetryEvent(value)
  override def publishDiagnostics(diagnostics: PublishDiagnosticsParams): Unit =
    underlying.publishDiagnostics(diagnostics)
  override def showMessage(params: MessageParams): Unit =
    underlying.showMessage(params)
  override def showMessageRequest(
      params: ShowMessageRequestParams
  ): CompletableFuture[MessageActionItem] =
    underlying.showMessageRequest(params)
  val logsQueue = new ConcurrentLinkedDeque[MessageParams]()
  def garbageCollectLogs(): Unit = {
    while (logsQueue.size() > 20) logsQueue.pollLast()
  }
  override def logMessage(message: MessageParams): Unit = {
    garbageCollectLogs()
    logsQueue.addFirst(message)
    triggerReload()
    underlying.logMessage(message)
  }
  def logs: String = {
    val sb = new java.lang.StringBuilder()
    pprint.log(logsQueue)
    logsQueue.forEach { params =>
      sb.append(params.getType.toString.toLowerCase)
        .append(" ")
        .append(params.getMessage)
        .append("\n")
    }
    sb.toString
  }

  val lspTrace = GlobalTrace.protocolTracePath("LSP")
  val bspTrace = GlobalTrace.protocolTracePath("BSP")

  private def serverCommands: String =
    List(
      "Import build" -> ServerCommands.ImportBuild,
      "Connect to build server" -> ServerCommands.ConnectBuildServer,
      "Scan workspace sources" -> ServerCommands.ScanWorkspaceSources
    ).map {
        case (what, id) =>
          s"""
             |<p>
             |<form action="/execute-command?command=$id" method="post">
             |  $what: <button type="submit">Execute</button>
             |</form>
             |</p>
       """.stripMargin
      }
      .mkString("\n")

  def renderHtml: String =
    s"""|<html>
        |<head>
        |    <title>Metals</title>
        |    <meta charset="UTF-8">
        |    <script src="$url/livereload.js"></script>
        |</head>
        |<body>
        |<h2>Extensions</h2>
        |<p>
        |Status: ${Icons.translate(icons, Icons.unicode, status.get())}
        |</p>
        |<h2>Commands</h2>
        |$serverCommands
        |<p>
        |<h2>Window</h2>
        |<p>
        |Log: ${workspace.resolve(Directories.log)}
        |<pre style="overflow:auto;height: 200px">
        |$logs
        |</pre>
        |<h2>JSON Traces</h2>
        |<p>
        |LSP trace: $lspTrace
        |</p>
        |<p>
        |BSP trace: $bspTrace
        |</p>
        |</p>
        |</p>
        |</body>
        |</html>
        |""".stripMargin

}
