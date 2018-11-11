package scala.meta.internal.metals

import com.zaxxer.nuprocess.NuAbstractProcessHandler
import com.zaxxer.nuprocess.NuProcess
import com.zaxxer.nuprocess.NuProcessBuilder
import fansi.ErrorMode
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.internal.metals.BuildTool.Sbt
import scala.meta.internal.metals.Messages._
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.SbtChecksum.Status
import scala.meta.io.AbsolutePath
import scala.util.Success

/**
 * Runs `sbt bloopInstall` processes.
 *
 * Handles responsibilities like:
 * - persist .metals/sbt.md5 checksum file after successful bloopInstall
 * - automatically install sbt-bloop and sbt-metals via -addPluginSbt feature
 *   introduced in sbt v1.2.0.
 */
final class BloopInstall(
    workspace: AbsolutePath,
    languageClient: MetalsLanguageClient,
    sh: ScheduledExecutorService,
    time: Time,
    tables: Tables
)(implicit ec: ExecutionContext, statusBar: StatusBar)
    extends Cancelable {
  private val cancelables = new MutableCancelable()
  override def cancel(): Unit = {
    cancelables.cancel()
  }

  override def toString: String = s"BloopInstall($workspace)"

  def runUnconditionally(): Future[BloopInstallResult] = {
    val tmp = BloopInstall.addPluginSbtFile()
    BloopInstall.workAroundIssue4395()
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
    val prettyArgs = args.mkString(" ").replace("; ", "")
    scribe.info(s"running $prettyArgs")
    // NOTE(olafur): older versions of VS Code don't respect cancellation of
    // window/showMessageRequest, meaning the "cancel build import" button
    // stays forever in view even after successful build import. In newer
    // VS Code versions the message is hidden after a delay.
    val taskResponse =
      languageClient.metalsSlowTask(Messages.BloopInstallProgress)
    handler.response = Some(taskResponse)
    val processFuture = handler.completeProcess.future.map { result =>
      taskResponse.cancel(true)
      scribe.info(s"time: Ran 'sbt bloopInstall' in $elapsed")
      result
    }
    taskResponse.asScala.foreach { item =>
      if (item.cancel) {
        scribe.info("User cancelled build import")
        handler.completeProcess.complete(
          Success(BloopInstallResult.Cancelled)
        )
        BloopInstall.destroyProcess(runningProcess)
        """java.util.concurrent.CompletionException: org.h2.jdbc.JdbcSQLException: The object is already closed [90007-197]
          |	at java.util.concurrent.CompletableFuture.encodeThrowable(CompletableFuture.java:292)
          |	at java.util.concurrent.CompletableFuture.completeThrowable(CompletableFuture.java:308)
          |	at java.util.concurrent.CompletableFuture.uniAccept(CompletableFuture.java:647)
          |	at java.util.concurrent.CompletableFuture$UniAccept.tryFire(CompletableFuture.java:632)
          |	at java.util.concurrent.CompletableFuture.postComplete(CompletableFuture.java:474)
          |	at java.util.concurrent.CompletableFuture.completeExceptionally(CompletableFuture.java:1977)
          |	at scala.concurrent.java8.FuturesConvertersImpl$CF.apply(FutureConvertersImpl.scala:21)
          |	at scala.concurrent.java8.FuturesConvertersImpl$CF.apply(FutureConvertersImpl.scala:18)
          |	at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:60)
          |	at scala.concurrent.BatchingExecutor$Batch.processBatch$1(BatchingExecutor.scala:63)
          |	at scala.concurrent.BatchingExecutor$Batch.$anonfun$run$1(BatchingExecutor.scala:78)
          |	at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:12)
          |	at scala.concurrent.BlockContext$.withBlockContext(BlockContext.scala:81)
          |	at scala.concurrent.BatchingExecutor$Batch.run(BatchingExecutor.scala:55)
          |	at scala.concurrent.Future$InternalCallbackExecutor$.unbatchedExecute(Future.scala:870)
          |	at scala.concurrent.BatchingExecutor.execute(BatchingExecutor.scala:106)
          |	at scala.concurrent.BatchingExecutor.execute$(BatchingExecutor.scala:103)
          |	at scala.concurrent.Future$InternalCallbackExecutor$.execute(Future.scala:868)
          |	at scala.concurrent.impl.CallbackRunnable.executeWithValue(Promise.scala:68)
          |	at scala.concurrent.impl.Promise$DefaultPromise.$anonfun$tryComplete$1(Promise.scala:284)
          |	at scala.concurrent.impl.Promise$DefaultPromise.$anonfun$tryComplete$1$adapted(Promise.scala:284)
          |	at scala.concurrent.impl.Promise$DefaultPromise.tryComplete(Promise.scala:284)
          |	at scala.concurrent.Promise.complete(Promise.scala:49)
          |	at scala.concurrent.Promise.complete$(Promise.scala:48)
          |	at scala.concurrent.impl.Promise$DefaultPromise.complete(Promise.scala:183)
          |	at scala.concurrent.impl.Promise.$anonfun$transform$1(Promise.scala:29)
          |	at scala.concurrent.impl.CallbackRunnable.run(Promise.scala:60)
          |	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
          |	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
          |	at java.lang.Thread.run(Thread.java:748)
          |Caused by: org.h2.jdbc.JdbcSQLException: The object is already closed [90007-197]
          |	at org.h2.message.DbException.getJdbcSQLException(DbException.java:357)
          |	at org.h2.message.DbException.get(DbException.java:179)
          |	at org.h2.message.DbException.get(DbException.java:155)
          |	at org.h2.message.DbException.get(DbException.java:144)
          |	at org.h2.jdbc.JdbcConnection.checkClosed(JdbcConnection.java:1523)
          |	at org.h2.jdbc.JdbcConnection.checkClosed(JdbcConnection.java:1502)
          |	at org.h2.jdbc.JdbcConnection.prepareStatement(JdbcConnection.java:302)
          |	at scala.meta.internal.metals.JdbcEnrichments$XtensionConnection.update(JdbcEnrichments.scala:14)
          |	at scala.meta.internal.metals.SbtChecksums.setStatus(SbtChecksums.scala:15)
          |	at scala.meta.internal.metals.BloopInstall.$anonfun$reimportIfChanged$10(BloopInstall.scala:149)
          |	at scala.meta.internal.metals.BloopInstall.$anonfun$reimportIfChanged$10$adapted(BloopInstall.scala:147)
          |	at scala.Option.foreach(Option.scala:257)
          |	at scala.meta.internal.metals.BloopInstall.$anonfun$reimportIfChanged$9(BloopInstall.scala:147)
          |	at scala.meta.internal.metals.BloopInstall.$anonfun$reimportIfChanged$9$adapted(BloopInstall.scala:146)
          |	at scala.Option.foreach(Option.scala:257)
          |	at scala.meta.internal.metals.BloopInstall.$anonfun$reimportIfChanged$8(BloopInstall.scala:146)
          |	at scala.util.Success.$anonfun$map$1(Try.scala:251)
          |	at scala.util.Success.map(Try.scala:209)
          |	at scala.concurrent.Future.$anonfun$map$1(Future.scala:288)
          |	at scala.concurrent.impl.Promise.liftedTree1$1(Promise.scala:29)
          |	... 5 more
          |""".stripMargin
      } else {
        processFuture.trackInStatusBar("$(sync) sbt bloopInstall")
      }
    }
    cancelables
      .add(() => BloopInstall.destroyProcess(runningProcess))
      .add(() => taskResponse.cancel(true))
    processFuture
  }

  def reimportIfChanged(
      buildTools: BuildTools,
      workspace: AbsolutePath,
      languageClient: MetalsLanguageClient,
      forceImport: Boolean
  )(implicit ec: ExecutionContextExecutorService): Future[BloopInstallResult] = {
    for {
      sbt <- buildTools.asSbt
      if {
        val isSupportedSbtVersion = SbtVersion.isSupported(sbt.version)
        if (!isSupportedSbtVersion) {
          reportUnsupportedSbtVersion(sbt)
        }
        isSupportedSbtVersion
      }
      current <- SbtChecksum.current(workspace)
      if forceImport || (tables.sbtChecksums.getStatus(current) match {
        case Some(status) if !status.isCancelled =>
          scribe.info(s"skipping build import with status '$status'")
          false
        case _ =>
          true
      })
    } yield {
      tables.sbtChecksums.setStatus(current, Status.Requested)
      for {
        userResponse <- BloopInstall.requestImport(
          buildTools,
          languageClient,
          forceImport
        )
        installResult <- {
          if (userResponse.isYes) {
            SbtChecksum
              .current(workspace)
              .foreach { checksum =>
                tables.sbtChecksums.setStatus(checksum, Status.Started)
              }
            runUnconditionally()
          } else {
            Future.successful(BloopInstallResult.Rejected)
          }
        }
      } yield {
        for {
          status <- installResult.toChecksumStatus
          checksum <- SbtChecksum.current(workspace)
        } {
          tables.sbtChecksums.setStatus(checksum, status)
        }
        installResult
      }
    }
  }.getOrElse(Future.successful(BloopInstallResult.Unchanged))

  private val pendingNotification = new AtomicBoolean(false)
  private def reportUnsupportedSbtVersion(sbt: Sbt): Unit = {
    statusBar.addMessage(IncompatibleSbtVersion.statusBar(sbt))
    val notification = tables.dismissedNotifications.IncompatibleSbt
    if (!notification.isDismissed &&
      pendingNotification.compareAndSet(false, true)) {
      languageClient
        .showMessageRequest(IncompatibleSbtVersion.params(sbt))
        .asScala
        .foreach { item =>
          pendingNotification.set(false)
          if (item == IncompatibleSbtVersion.dismissForever) {
            notification.dismissForever()
          } else if (item == IncompatibleSbtVersion.learnMore) {
            Urls.openBrowser(IncompatibleSbtVersion.learnMoreUrl)
          } else {
            notification.dismiss(1, TimeUnit.DAYS)
          }
        }
    }
  }

}

object BloopInstall {

  private def requestImport(
      buildTools: BuildTools,
      languageClient: MetalsLanguageClient,
      forceImport: Boolean
  )(implicit ec: ExecutionContext): Future[Confirmation] = {
    if (forceImport) Future.successful(Confirmation.Yes)
    else if (buildTools.isBloop) {
      languageClient
        .showMessageRequest(ReimportSbtProject.params)
        .asScala
        .map { item =>
          Confirmation.fromBoolean(item == ReimportSbtProject.yes)
        }
    } else {
      languageClient
        .showMessageRequest(ImportBuildViaBloop.params)
        .asScala
        .map { item =>
          Confirmation.fromBoolean(item == ImportBuildViaBloop.yes)
        }
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

  /**
   * Creates a temporary `*.sbt` file to use with -addPluginSbtFile.
   */
  private def addPluginSbtFile(): Path = {
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
   * Converts running system processing into Future[BloopInstallResult].
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
          completeProcess.trySuccess(BloopInstallResult.Installed)
        } else {
          completeProcess.trySuccess(BloopInstallResult.Failed(statusCode))
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
