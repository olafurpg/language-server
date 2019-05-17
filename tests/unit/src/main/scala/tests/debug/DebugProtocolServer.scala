package tests.debug

import java.net.URI
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.debug.DebugAdapter
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.CompilationScheduler
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.MetalsDebugAdapter
import scala.meta.internal.metals.debug.protocol.LaunchParameters

protected[debug] final class DebugProtocolServer(
    workspace: URI,
    server: MetalsDebugAdapter,
    buildTargets: BuildTargets
) extends DebugAdapter {

  def initialize(
      arguments: InitializeRequestArguments
  ): Future[Capabilities] = {
    server.initialize(arguments).asScala
  }

  /**
   * Assumption: class to be run and file have the same name
   */
  def launch(file: String): Future[Unit] = {
    val target = workspace.resolve(file).toAbsolutePath
    val mainClass = target.filename.dropRight(".scala".length)

    val parameters = LaunchParameters(
      workspace.toString,
      mainClass,
      buildTargets.inverseSources(target).get
    )

    server.launch(parameters).asScala
  }

  override def setClient(client: IDebugProtocolClient): Unit =
    server.setClient(client)
}

object DebugProtocolServer {
  def apply(
      workspace: URI,
      compilationScheduler: CompilationScheduler,
      buildTargets: BuildTargets
  )(implicit ec: ExecutionContext): DebugProtocolServer = {
    val metalsAdapter = MetalsDebugAdapter(compilationScheduler, buildTargets)
    new DebugProtocolServer(workspace, metalsAdapter, buildTargets)
  }
}
