package scala.meta.internal.debug

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.debug
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContext
import scala.meta.internal.debug.protocol.ExitedEventArguments
import scala.meta.internal.debug.protocol.LaunchParameters

final class JvmDebugAdapter(implicit val ex: ExecutionContext)
    extends DebugAdapter {
  private val client = new ClientDelegate
  private val sessionFactory = new DebugSessionFactory(client)

  @JsonRequest
  def initialize(
      args: debug.InitializeRequestArguments
  ): CompletableFuture[
    debug.Capabilities
  ] = {
    val capabilities = new debug.Capabilities()
    CompletableFuture.completedFuture(capabilities)
  }

  @JsonRequest
  def launch(
      params: LaunchParameters
  ): CompletableFuture[Unit] = {
    val session = sessionFactory.create(params)

    session.whenTerminated { exitCode =>
      client.terminated(new debug.TerminatedEventArguments)
      client.exited(ExitedEventArguments(exitCode))
    }

    CompletableFuture.completedFuture(())
  }

  override def setClient(client: IDebugProtocolClient): Unit = {
    this.client.setUnderlying(client)
  }
}

object JvmDebugAdapter {
  def apply(
      client: IDebugProtocolClient
  )(implicit ec: ExecutionContext): JvmDebugAdapter = {
    val adapter = new JvmDebugAdapter()
    adapter.setClient(client)
    adapter
  }
}
