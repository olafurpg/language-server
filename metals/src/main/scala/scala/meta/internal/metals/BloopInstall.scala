package scala.meta.internal.metals

import com.zaxxer.nuprocess.NuAbstractProcessHandler
import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import fansi.ErrorMode
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.io.AbsolutePath
import scala.util.Success
import MetalsEnrichments._

final class BloopInstall(
    workspace: AbsolutePath,
    languageClient: MetalsLanguageClient,
    cancelables: MutableCancelable,
    sh: ScheduledExecutorService,
    time: Time
)(implicit ec: ExecutionContext, statusBar: StatusBar) {

  def runAndPersistChecksum(checksum: String): Future[BloopInstallResult] = {
    val bloopInstallProcess = runUnconditionally()
    cancelables.add(bloopInstallProcess)
    bloopInstallProcess.map { result =>
      if (result.isInstalled) {
        val out = workspace.resolve(SbtChecksum.Md5)
        Files.createDirectories(out.toNIO.getParent)
        Files.write(
          out.toNIO,
          checksum.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
      }
      result
    }
  }

  // Creates ~/.sbt/1.0/plugins if it doesn't exist, see
  // https://github.com/sbt/sbt/issues/4395
  private def workAroundIssue4395(): Unit = {
    val plugins = AbsolutePath(System.getProperty("user.home"))
      .resolve(".sbt")
      .resolve("1.0")
      .resolve("plugins")
    Files.createDirectories(plugins.toNIO)
  }

  def runUnconditionally(): CancelableFuture[BloopInstallResult] = {
    val tmp = BloopInstall.bloopSbt()
    workAroundIssue4395()
    val elapsed = new Timer(time)
    val handler = new BloopInstall.ProcessHandler()
    val args = List(
      s"""sbt""",
      s"""-Dscalameta.version=4.0.0-163560a8""",
      s"""-Djline.terminal=jline.UnsupportedTerminal""",
      s"""-Dsbt.log.noformat=true""",
      s"""-Dfile.encoding=UTF-8""",
      s"""-addPluginSbtFile=$tmp""",
      s"""set bloopExportJarClassifiers in Global := Some(Set("sources"))""",
      s"""semanticdbEnable""",
      s"""bloopInstall""",
      s"""exit"""
    )
    val pb = new NuProcessBuilder(handler, args.asJava)
    pb.setCwd(workspace.toNIO)
    pb.environment().put("COURSIER_PROGRESS", "disable")
    val runningProcess = pb.start()
    statusBar.addFuture(
      "$(importing) Importing build",
      handler.completeProcess.future
    )
    val prettyArgs = args.mkString(" ").replace("; ", "")
    scribe.info(s"running $prettyArgs")
    // NOTE(olafur): older versions of VS Code don't respect cancellation of
    // window/showMessageRequest, meaning the "cancel build import" button
    // stays forever in view even after successful build import. In newer
    // VS Code versions the message is hidden after a delay.
    val taskResponse =
      languageClient.metalsSlowTask(Messages.BloopInstallProgress)
    handler.response = Some(taskResponse)
    taskResponse.toScala.foreach { item =>
      if (item.cancel) {
        scribe.info("User cancelled build import")
        handler.completeProcess.complete(
          Success(BloopInstallResult.Cancelled)
        )
        BloopInstall.destroyProcess(runningProcess)
      }
    }
    val processFuture = handler.completeProcess.future.map { result =>
      taskResponse.cancel(true)
      scribe.info(s"time: Ran 'sbt bloopInstall' in $elapsed")
      result
    }
    processFuture.trackInStatusBar("$(sync) Importing build")
    val cancelable = new MutableCancelable()
      .add(() => BloopInstall.destroyProcess(runningProcess))
      .add(() => taskResponse.cancel(true))
    CancelableFuture(cancelable, processFuture)
  }

}

object BloopInstall {

  private def bloopSbt(): Path = {
    val text =
      """|
         |val bloopVersion = "1.0.0"
         |val bloopModule = "ch.epfl.scala" % "sbt-bloop" % bloopVersion
         |val metalsVersion = "SNAPSHOT"
         |val metalsModule = "org.scalameta" % "sbt-metals" % metalsVersion
         |libraryDependencies := {
         |  import Defaults.sbtPluginExtra
         |  val oldDependencies = libraryDependencies.value
         |  val bloopArtifacts =
         |    oldDependencies.filter(d => d.organization == "ch.epfl.scala" && d.name == "sbt-bloop")
         |
         |  // Only add the plugin if it cannot be found in the current library dependencies
         |  if (!bloopArtifacts.isEmpty) oldDependencies
         |  else {
         |    val sbtVersion = (Keys.sbtBinaryVersion in pluginCrossBuild).value
         |    val scalaVersion = (Keys.scalaBinaryVersion in update).value
         |    val bloopPlugin = sbtPluginExtra(bloopModule, sbtVersion, scalaVersion)
         |    val metalsPlugin = sbtPluginExtra(metalsModule, sbtVersion, scalaVersion)
         |    List(bloopPlugin, metalsPlugin) ++ oldDependencies
         |  }
         |}
         |""".stripMargin

    val tmp = Files.createTempDirectory("metals").resolve("bloop.sbt")
    Files.write(
      tmp,
      text.getBytes(StandardCharsets.UTF_8)
    )
    tmp
  }

  private def unchangedResult: CancelableFuture[BloopInstallResult] =
    CancelableFuture(
      Cancelable.empty,
      Future.successful(BloopInstallResult.Unchanged)
    )

  /**
   * First tries to destroy the process gracefully, with fallback to forcefully.
   */
  private def destroyProcess(process: NuProcess): Unit = {
    process.destroy(false)
    val exit = process.waitFor(2, TimeUnit.SECONDS)
    if (exit == Integer.MIN_VALUE) {
      // timeout exceeded, kill process forcefully.
      process.destroy(true)
      process.waitFor(2, TimeUnit.SECONDS)
    }
  }

  /**
   * Process handler that produces a Future[BloopInstallResult].
   */
  private class ProcessHandler() extends NuAbstractProcessHandler {
    var response: Option[CompletableFuture[_]] = None
    val completeProcess = Promise[BloopInstallResult]()

    override def onStart(nuProcess: NuProcess): Unit = {
      nuProcess.closeStdin(false)
    }

    override def onExit(statusCode: Int): Unit = {
      if (!completeProcess.isCompleted) {
        if (statusCode == 0) {
          completeProcess.success(BloopInstallResult.Installed)
        } else {
          completeProcess.success(BloopInstallResult.Failed(statusCode))
        }
      }
      scribe.info(s"sbt exit: $statusCode")
      response.foreach(_.cancel(true))
    }

    override def onStdout(buffer: ByteBuffer, closed: Boolean): Unit = {
      log(closed, buffer)(out => scribe.info(out))
    }

    override def onStderr(buffer: ByteBuffer, closed: Boolean): Unit = {
      log(closed, buffer)(out => scribe.error(out))
    }

    private def log(closed: Boolean, buffer: ByteBuffer)(
        fn: String => Unit
    ): Unit = {
      if (!closed) {
        val text = toPlainString(buffer).trim
        if (text.nonEmpty) {
          fn(text)
        }
      }
    }

    private def toPlainString(buffer: ByteBuffer): String = {
      val bytes = new Array[Byte](buffer.remaining())
      buffer.get(bytes)
      val ansiString = new String(bytes, StandardCharsets.UTF_8)
      fansi.Str(ansiString, ErrorMode.Sanitize).plainText
    }
  }
}
