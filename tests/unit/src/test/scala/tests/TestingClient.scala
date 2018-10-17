package tests

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import scala.collection.concurrent.TrieMap
import scala.meta.inputs.Position
import scala.meta.internal.metals.Messages._
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.PositionSyntax._
import scala.meta.internal.metals.Buffers
import scala.meta.internal.metals.MetalsLanguageClient
import scala.meta.internal.metals.MetalsSlowTaskParams
import scala.meta.internal.metals.MetalsSlowTaskResult
import scala.meta.internal.metals.MetalsStatusParams
import scala.meta.io.AbsolutePath

final class TestingClient(workspace: AbsolutePath, buffers: Buffers)
    extends MetalsLanguageClient {
  val diagnostics = TrieMap.empty[AbsolutePath, Seq[Diagnostic]]
  val diagnosticsCount = TrieMap.empty[AbsolutePath, AtomicInteger]
  val messageRequests = new ConcurrentLinkedQueue[String]()
  val showMessages = new ConcurrentLinkedQueue[MessageParams]()
  val statusMessage =
    new AtomicReference[MetalsStatusParams](MetalsStatusParams(""))
  var slowTaskHandler: MetalsSlowTaskParams => Option[MetalsSlowTaskResult] = {
    _: MetalsSlowTaskParams =>
      None
  }

  def workspaceShowMessages: String = {
    showMessages.asScala.map(_.getMessage).mkString("\n")
  }
  def workspaceMessageRequests: String = {
    messageRequests.asScala.mkString("\n")
  }
  def workspaceDiagnostics: String = {
    val sb = new StringBuilder
    val paths = diagnostics.keys.toList.sortBy(_.toURI.toString)
    paths.foreach { abspath =>
      val diags = diagnostics(abspath)
      val relpath =
        abspath.toRelative(workspace).toURI(isDirectory = false).toString
      val input =
        abspath.toInputFromBuffers(buffers).copy(path = relpath)
      diags.foreach { diag =>
        val start = diag.getRange.getStart
        val end = diag.getRange.getEnd
        val pos = Position.Range(
          input,
          start.getLine,
          start.getCharacter,
          end.getLine,
          end.getCharacter
        )
        val message = pos.formatMessage(
          diag.getSeverity.toString.toLowerCase(),
          diag.getMessage
        )
        sb.append(message)
          .append("\n")
      }
    }
    sb.toString()
  }
  override def telemetryEvent(`object`: Any): Unit = ()
  override def publishDiagnostics(params: PublishDiagnosticsParams): Unit = {
    val path = params.getUri.toAbsolutePath
    diagnostics(path) = params.getDiagnostics.asScala
    diagnosticsCount
      .getOrElseUpdate(path, new AtomicInteger())
      .incrementAndGet()
  }
  override def showMessage(params: MessageParams): Unit = {
    showMessages.add(params)
  }
  override def showMessageRequest(
      params: ShowMessageRequestParams
  ): CompletableFuture[MessageActionItem] =
    CompletableFuture.completedFuture {
      messageRequests.add(params.getMessage)
      if (params == ReimportSbtProject.params) {
        ReimportSbtProject.yes
      } else if (params == ImportProjectViaBloop.params) {
        ImportProjectViaBloop.yes
      } else {
        throw new IllegalArgumentException(params.toString)
      }
    }
  override def logMessage(params: MessageParams): Unit =
    params.getType match {
      case MessageType.Info => println(params.getMessage)
      case MessageType.Warning => scribe.warn(params.getMessage)
      case _ => scribe.error(params.getMessage)
    }
  override def metalsSlowTask(
      params: MetalsSlowTaskParams
  ): CompletableFuture[MetalsSlowTaskResult] = {
    CompletableFuture.completedFuture {
      messageRequests.add(params.message)
      slowTaskHandler(params) match {
        case Some(result) =>
          result
        case None =>
          MetalsSlowTaskResult(cancel = false)
      }
    }
  }

  override def metalsStatus(params: MetalsStatusParams): Unit = {
    statusMessage.set(params)
  }
}
