package scala.meta.internal.metals

import ch.epfl.scala.{bsp4j => b}
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.{lsp4j => l}

/**
 * A build client that forwards notifications from the build server to the language client.
 */
final class ForwardingMetalsBuildClient(
    languageClient: LanguageClient,
    diagnostics: Diagnostics
) extends MetalsBuildClient {

  private var buildServer: Option[b.BuildServer] = None
  def onBuildShowMessage(params: l.MessageParams): Unit =
    languageClient.showMessage(params)

  def onBuildLogMessage(params: l.MessageParams): Unit =
    languageClient.logMessage(params)

  def onBuildPublishDiagnostics(params: b.PublishDiagnosticsParams): Unit = {
    diagnostics.onBuildPublishDiagnostics(params)
  }

  def onBuildTargetDidChange(params: b.DidChangeBuildTarget): Unit = {
    scribe.info(params.toString)
  }

  def onBuildTargetCompileReport(params: b.CompileReport): Unit = {
    diagnostics.onBuildTargetCompileReport(params)
  }

  def onConnect(server: b.BuildServer): Unit = {
    this.buildServer = Some(server)
  }
}
