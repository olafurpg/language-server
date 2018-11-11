package scala.meta.internal.metals

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.services.LanguageClient

/**
 * An implementation of MetalsLanguageClient that ignores custom Metals extension like `metals/status`.
 */
class NoExtensionLanguageClient(underlying: LanguageClient)
    extends MetalsLanguageClient {
  override def metalsStatus(params: MetalsStatusParams): Unit = ()
  override def metalsSlowTask(
      params: MetalsSlowTaskParams
  ): CompletableFuture[MetalsSlowTaskResult] = {
    CompletableFuture.completedFuture(MetalsSlowTaskResult(cancel = false))
  }
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
}
