package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.TaskDataKind
import ch.epfl.scala.bsp4j.TaskFinishParams
import ch.epfl.scala.bsp4j.TaskStartParams
import ch.epfl.scala.{bsp4j => b}
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.{lsp4j => l}
import MetalsEnrichments._
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.CompileReport
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.TrieMap
import scala.concurrent.Promise

/**
 * A build client that forwards notifications from the build server to the language client.
 */
final class ForwardingMetalsBuildClient(
    languageClient: LanguageClient,
    diagnostics: Diagnostics,
    buildTargets: BuildTargets,
    config: MetalsServerConfig,
    statusBar: StatusBar,
    time: Time
) extends MetalsBuildClient
    with Cancelable {

  private case class Compilation(
      timer: Timer,
      promise: Promise[CompileReport]
  )

  private val compilations = TrieMap.empty[BuildTargetIdentifier, Compilation]
  private val hasReportedError = Collections.newSetFromMap(
    new ConcurrentHashMap[BuildTargetIdentifier, java.lang.Boolean]()
  )

  override def cancel(): Unit = {
    compilations.values.foreach { compilation =>
      compilation.promise.tryFailure(new CancellationException())
    }
  }

  def onBuildShowMessage(params: l.MessageParams): Unit =
    languageClient.showMessage(params)

  def onBuildLogMessage(params: l.MessageParams): Unit =
    params.getType match {
      case l.MessageType.Error =>
        scribe.error(params.getMessage)
      case l.MessageType.Warning =>
        scribe.warn(params.getMessage)
      case l.MessageType.Info =>
        scribe.info(params.getMessage)
      case l.MessageType.Log =>
        scribe.info(params.getMessage)
    }

  def onBuildPublishDiagnostics(params: b.PublishDiagnosticsParams): Unit = {
    diagnostics.onBuildPublishDiagnostics(params)
  }

  def onBuildTargetDidChange(params: b.DidChangeBuildTarget): Unit = {
    scribe.info(params.toString)
  }

  def onBuildTargetCompileReport(params: b.CompileReport): Unit = {}

  // We ignore task{Start,Finish} notifications for now.
  @JsonNotification("build/taskStart")
  def buildTaskStart(params: TaskStartParams): Unit = {
    params.getDataKind match {
      case TaskDataKind.COMPILE_TASK =>
        for {
          task <- params.asCompileTask
          info <- buildTargets.info(task.getTarget)
        } {
          val name = info.getDisplayName
          val promise = Promise[CompileReport]()
          val compilation = Compilation(new Timer(time), promise)
          compilations(task.getTarget) = compilation
          statusBar.trackFuture(
            s"Compiling $name",
            promise.future,
            showTimer = true
          )
        }
      case _ =>
    }
  }

  @JsonNotification("build/taskFinish")
  def buildTaskEnd(params: TaskFinishParams): Unit = {
    params.getDataKind match {
      case TaskDataKind.COMPILE_REPORT =>
        for {
          report <- params.asCompileReport
          compilation <- compilations.get(report.getTarget)
        } {
          val target = report.getTarget
          compilation.promise.trySuccess(report)
          val name = buildTargets
            .info(report.getTarget)
            .fold(" <unknown>")(i => " " + i.getDisplayName)
          val isSuccess = report.getErrors == 0
          val icon = if (isSuccess) config.icons.check else config.icons.alert

          if (isSuccess) {
            hasReportedError.remove(target)
            statusBar.addMessage(
              s"${icon}Compiled$name (${compilation.timer})"
            )
          } else {
            hasReportedError.add(target)
            statusBar.addMessage(
              MetalsStatusParams(
                s"${icon}Compiled$name (${compilation.timer})",
                command = ClientCommands.FocusDiagnostics.id
              )
            )
          }

        }
      case _ =>
    }
  }
}
