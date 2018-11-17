package scala.meta.internal.metals

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams

final class MetalsHttpClient(
    url: String,
    underlying: MetalsLanguageClient,
    triggerReload: () => Unit
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
  override def logMessage(message: MessageParams): Unit =
    underlying.logMessage(message)

  def renderHtml: String =
    s"""|<html>
        |<head>
        |    <title>Metals</title>
        |    <meta charset="UTF-8">
        |    <script src="$url/livereload.js"></script>
        |</head>
        |<body>
        |<p>
        |Status: ${status.get()}
        |</p>
        |<p>
        |<form action="/execute-command&command=build.import" method="post">
        |  Import build: <button type="submit">Submit</button>
        |</form>
        |</p>
        |</body>
        |</html>
        |""".stripMargin

}
