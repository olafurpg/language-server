package scala.meta.internal.metals

import java.util.concurrent.CompletableFuture
import javax.annotation.Nullable
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient

trait MetalsLanguageClient extends LanguageClient {

  @JsonNotification("metals/status")
  def metalsStatus(params: MetalsStatusParams): Unit

  @JsonRequest("metals/slowTask")
  def metalsSlowTask(
      params: MetalsSlowTaskParams
  ): CompletableFuture[MetalsSlowTaskResult]

}

case class MetalsStatusParams(
    text: String,
    @Nullable show: java.lang.Boolean = null,
    @Nullable hide: java.lang.Boolean = null,
    @Nullable tooltip: String = null,
    @Nullable command: String = null
)

case class MetalsSlowTaskParams(message: String)
case class MetalsSlowTaskResult(cancel: Boolean)
