package scala.meta.internal.metals.debug

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContext
import scala.meta.internal.debug.DebugAdapter
import scala.meta.internal.debug.JvmDebugAdapter
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.CompilationScheduler
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.{protocol => metals}

/**
 * Metals receive requests without the full knowledge - e.g. without classpath.
 * Here, we provide such information
 */
class MetalsDebugAdapter(
    underlying: JvmDebugAdapter,
    parametersAdapter: DebugParametersAdapter
)(implicit ec: ExecutionContext)
    extends DebugAdapter {

  @JsonRequest
  def launch(params: metals.LaunchParameters): CompletableFuture[Unit] = {
    parametersAdapter
      .adapt(params)
      .flatMap(underlying.launch(_).asScala)
      .asJava
  }

  @JsonRequest
  def initialize(
      args: InitializeRequestArguments
  ): CompletableFuture[Capabilities] =
    underlying.initialize(args)

  override def setClient(client: IDebugProtocolClient): Unit =
    underlying.setClient(client)
}

object MetalsDebugAdapter {
  def apply(
      compilationScheduler: CompilationScheduler,
      buildTargets: BuildTargets
  )(implicit ec: ExecutionContext): MetalsDebugAdapter = {
    val parametersAdapter =
      new DebugParametersAdapter(compilationScheduler, buildTargets)

    new MetalsDebugAdapter(new JvmDebugAdapter, parametersAdapter)
  }
}
