package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.CompileReport
import ch.epfl.scala.bsp4j.DidChangeBuildTarget
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams
import com.geirsson.coursiersmall
import com.google.gson.JsonArray
import java.io.InputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.scalasbt.ipcsocket.UnixDomainSocket
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath
import scala.sys.process.Process
import scala.sys.process.ProcessLogger
import scala.util.Random
import scala.util.Success
import scala.util.control.NonFatal

final class BloopServer(
    sh: ScheduledExecutorService,
    workspace: AbsolutePath
)(implicit ec: ExecutionContextExecutorService) {

  def connect(buildClient: MetalsBuildClient): Future[BuildServerConnection] = {
    for {
      (bloop, bloobCancelable) <- establishLocalConnection(workspace)
    } yield {
      val tracePrinter = GlobalTrace.setupTracePrinter("BSP")
      val launcher = new Launcher.Builder[MetalsBuildServer]()
        .traceMessages(tracePrinter)
        .setRemoteInterface(classOf[MetalsBuildServer])
        .setExecutorService(ec)
        .setInput(bloop.getInputStream)
        .setOutput(bloop.getOutputStream)
        .setLocalService(buildClient)
        .create()
      val listening = launcher.startListening()
      val remoteServer = launcher.getRemoteProxy
      buildClient.onConnect(remoteServer)
      val cancelables = List(
        Cancelable(() => {
          if (!bloop.isInputShutdown) bloop.shutdownInput()
          if (!bloop.isOutputShutdown) bloop.shutdownOutput()
          bloop.close()
        }),
        bloobCancelable,
        Cancelable(() => listening.cancel(true))
      )
      BuildServerConnection(workspace, buildClient, remoteServer, cancelables)
    }
  }

  private def newSocketFile(): AbsolutePath = {
    val tmp = Files.createTempDirectory("bsp")
    val id = java.lang.Long.toString(Random.nextLong(), Character.MAX_RADIX)
    val socket = tmp.resolve(s"$id.socket")
    socket.toFile.deleteOnExit()
    AbsolutePath(socket)
  }

  private def establishLocalConnection(
      workspace: AbsolutePath,
  ): Future[(UnixDomainSocket, Cancelable)] = {
    val socket = newSocketFile()
    val args = Array(
      "bsp",
      "--protocol",
      "local",
      "--socket",
      socket.toString
    )
    val cancelable = callBloop(args, workspace)
    for {
      confirmation <- waitForFileToBeCreated(socket)
    } yield {
      if (confirmation.isYes) {
        val connection = new UnixDomainSocket(socket.toFile.getCanonicalPath)
        (connection, cancelable)
      } else {
        cancelable.cancel()
        throw NoSocketFile(socket)
      }
    }
  }

  private def callBloop(
      args: Array[String],
      workspace: AbsolutePath
  ): Cancelable = {
    val logger = MetalsLogger.newBspLogger(workspace)
    if (bloopCommandLineIsInstalled(workspace)) {
      val bspProcess = Process(
        "bloop" +: args,
        cwd = workspace.toFile
      ).run(
        ProcessLogger(
          out => logger.info(out),
          err => logger.error(err)
        )
      )
      Cancelable(() => bspProcess.destroy())
    } else {
      bloopJars match {
        case Some(classloaders) =>
          val cancelMain = Promise[java.lang.Boolean]()
          val job = ec.submit(new Runnable {
            override def run(): Unit = {
              reflectivelyCallBloop(
                workspace,
                classloaders,
                args,
                cancelMain.future.toJavaCompletable
              )
            }
          })
          new MutableCancelable()
            .add(Cancelable(() => job.cancel(true)))
            .add(Cancelable(() => cancelMain.success(true)))
        case None =>
          Cancelable.empty
      }
    }
  }

  private def reflectivelyCallBloop(
      workspace: AbsolutePath,
      classLoader: ClassLoader,
      args: Array[String],
      cancelMain: CompletableFuture[java.lang.Boolean]
  ): Unit = {
    val cls = classLoader.loadClass("bloop.Cli")
    val reflectiveMain = cls.getMethod(
      "reflectMain",
      classOf[Array[String]],
      classOf[Path],
      classOf[InputStream],
      classOf[PrintStream],
      classOf[PrintStream],
      classOf[Properties],
      classOf[CompletableFuture[java.lang.Boolean]]
    )
    val ps = System.out
    val exitCode = reflectiveMain.invoke(
      null,
      args,
      workspace.toNIO,
      new InputStream { override def read(): Int = -1 },
      ps,
      ps,
      new Properties(),
      cancelMain
    )
    scribe.info(s"bloop exit: $exitCode")
  }

  private def bloopCommandLineIsInstalled(workspace: AbsolutePath): Boolean = {
    try {
      val output = Process(List("bloop", "help"), cwd = workspace.toFile)
        .!!(ProcessLogger(_ => ()))
      // NOTE: our BSP integration requires bloop 1.1 or higher so we ensure
      // users are on an older version.
      val isOldVersion =
        output.startsWith("bloop 1.0") ||
          output.contains(bloopVersion) ||
          output.startsWith("bloop 0")
      !isOldVersion
    } catch {
      case NonFatal(_) =>
        false
    }
  }

  case class NoSocketFile(file: AbsolutePath)
      extends Exception(s"no connection: $file")

  private def waitForFileToBeCreated(
      socket: AbsolutePath,
  ): Future[Confirmation] = {
    val retryDelayMillis: Long = 200
    val maxRetries: Int = 40
    val promise = Promise[Confirmation]()
    var remainingRetries = maxRetries
    val tick = sh.scheduleAtFixedRate(
      new Runnable {
        override def run(): Unit = {
          if (Files.exists(socket.toNIO)) {
            promise.complete(Success(Confirmation.Yes))
          } else if (remainingRetries < 0) {
            promise.complete(Success(Confirmation.No))
          } else {
            remainingRetries -= 1
          }
        }
      },
      0,
      retryDelayMillis,
      TimeUnit.MILLISECONDS
    )
    promise.future.onComplete(_ => tick.cancel(true))
    promise.future
  }

  lazy val bloopJars: Option[ClassLoader] = {
    try Some(newBloopClassloader())
    catch {
      case NonFatal(e) =>
        scribe.error("Failed to classload bloop, compilation will not work", e)
        None
    }
  }

  private def newBloopClassloader(): ClassLoader = {
    val settings = new coursiersmall.Settings()
      .withDependencies(
        List(
          new coursiersmall.Dependency(
            "ch.epfl.scala",
            "bloop-frontend_2.12",
            bloopVersion
          )
        )
      )
      .withRepositories(
        new coursiersmall.Settings().repositories ++ List(
          coursiersmall.Repository.SonatypeReleases
        )
      )
    val jars = coursiersmall.CoursierSmall.fetch(settings)
    val classloader =
      new URLClassLoader(jars.iterator.map(_.toUri.toURL).toArray, null)
    classloader
  }

  def bloopVersion: String = System.getProperty("bloop.version", "121807cc")
}

object BloopServer {
  def compile(bloopServer: BloopServer, targets: List[String])(
      implicit ec: ExecutionContextExecutorService
  ): Future[Unit] = {
    val client = new MetalsBuildClient {
      override def onBuildShowMessage(params: MessageParams): Unit =
        pprint.log(params)
      override def onBuildLogMessage(
          params: MessageParams
      ): Unit = pprint.log(params)
      override def onBuildPublishDiagnostics(
          params: PublishDiagnosticsParams
      ): Unit = pprint.log(params)
      override def onBuildTargetDidChange(
          params: DidChangeBuildTarget
      ): Unit = pprint.log(params)
      override def onBuildTargetCompileReport(params: CompileReport): Unit =
        pprint.log(params)
      override def onConnect(remoteServer: BuildServer): Unit =
        pprint.log(remoteServer)
    }
    for {
      bloop <- bloopServer.connect(client)
      _ <- bloop.initialize()
      buildTargets <- bloop.server.workspaceBuildTargets().toScala
      ids = buildTargets.getTargets.asScala
        .filter(target => targets.contains(target.getDisplayName))
      names = ids.map(_.getDisplayName).mkString(" ")
      _ = scribe.info(s"compiling: $names")
      params = new CompileParams(ids.map(_.getId).asJava)
      _ = params.setArguments(new JsonArray)
      _ <- bloop.server.buildTargetCompile(params).toScala
      _ <- bloop.shutdown()
    } yield {
      scribe.info("done!")
    }
  }
  def main(args: Array[String]): Unit = {
    args.toList match {
      case directory :: "compile" :: targets =>
        val sh = Executors.newSingleThreadScheduledExecutor()
        val ex = Executors.newCachedThreadPool()
        implicit val ec = ExecutionContext.fromExecutorService(ex)
        val workspace = AbsolutePath(directory)
        val server = new BloopServer(sh, workspace)
        try {
          val future = compile(server, targets)
          Await.result(future, Duration("1min"))
        } finally {
          sh.shutdown()
          ex.shutdown()
        }
        println("goodbye!")
      case els =>
        System.err.println(
          s"expected '<workspace> compile [..targets]'. obtained $els"
        )
        System.exit(1)
    }
  }
}
