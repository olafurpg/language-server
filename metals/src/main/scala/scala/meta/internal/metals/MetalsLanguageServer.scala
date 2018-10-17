package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.DependencySourcesParams
import ch.epfl.scala.bsp4j.ScalacOptionsParams
import com.google.gson.JsonArray
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.jsonrpc.CompletableFutures
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.Future
import scala.meta.internal.io.FileIO
import scala.meta.internal.io.PathIO
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.Mtags
import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.io.AbsolutePath
import scala.meta.parsers.ParseException
import scala.util.control.NonFatal

class MetalsLanguageServer(
    ec: ExecutionContextExecutorService,
    config: MetalsServerConfig = MetalsServerConfig.default,
    buffers: Buffers = Buffers(),
    redirectSystemOut: Boolean = true,
    charset: Charset = StandardCharsets.UTF_8,
    time: Time = Time.system
) extends Cancelable {

  private implicit val executionContext: ExecutionContextExecutorService = ec
  private val sh = Executors.newSingleThreadScheduledExecutor()
  private val fingerprints = new MutableMd5Fingerprints
  private val mtags = new Mtags
  private var workspace = PathIO.workingDirectory
  private val index = newSymbolIndex()
  private var buildServer = Option.empty[BuildServerConnection]
  private val openTextDocument = new AtomicReference[AbsolutePath]()

  private val cancelables = new MutableCancelable()
  override def cancel(): Unit = cancelables.cancel()

  // These can't be instantiated until we know the workspace root directory.
  private var languageClient: MetalsLanguageClient = _
  private var bloopInstall: BloopInstall = _
  private var diagnostics: Diagnostics = _
  private var buildTargets: BuildTargets = _
  private var compilers: Compilers = _
  private var buildTools: BuildTools = _
  private var textDocuments: AggregateTextDocuments = _
  private var buildClient: MetalsBuildClient = _
  private var bloopServer: BloopServer = _
  private implicit var statusBar: StatusBar = _

  def connectToLanguageClient(client: MetalsLanguageClient): Unit = {
    languageClient = client
    statusBar = new StatusBar(languageClient, time)
    LanguageClientLogger.languageClient = Some(client)
  }

  private def updateWorkspaceDirectory(params: InitializeParams): Unit = {
    workspace = AbsolutePath(Paths.get(URI.create(params.getRootUri)))
    buildTools = new BuildTools(workspace)
    buildTargets = new BuildTargets(buffers, charset, workspace, fingerprints)
    compilers = new Compilers(workspace, buildTargets, charset)
    diagnostics = new Diagnostics(buildTargets, languageClient)
    buildClient = new ForwardingMetalsBuildClient(languageClient, diagnostics)
    bloopInstall =
      new BloopInstall(workspace, languageClient, cancelables, sh, time)
    bloopServer = new BloopServer(sh, workspace)
    MetalsLogger.setupLspLogger(workspace, redirectSystemOut)
    textDocuments = AggregateTextDocuments(
      List(
        buildTargets,
        compilers
      )
    )
    cancelables.add(compilers)
  }

  @JsonRequest("initialize")
  def initialize(
      params: InitializeParams
  ): CompletableFuture[InitializeResult] =
    CompletableFuture.completedFuture {
      updateWorkspaceDirectory(params)
      val capabilities = new ServerCapabilities()
      capabilities.setDefinitionProvider(true)
      capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
      new InitializeResult(capabilities)
    }

  @JsonNotification("initialized")
  def initialized(params: InitializedParams): CompletableFuture[Unit] = {
    statusBar.start(sh, 1, 1, TimeUnit.SECONDS)
    Future
      .sequence(
        List[Future[Unit]](
          quickConnectToBuildServer().ignoreValue,
          indexWorkspaceScalaSources(),
          slowConnectToBuildServer(forceImport = false).ignoreValue
        )
      )
      .toJavaUnitCompletable
  }

  @JsonRequest("shutdown")
  def shutdown(): CompletableFuture[Unit] = {
    cancelables.cancel()
    sh.shutdownNow()
    for {
      _ <- buildServer match {
        case Some(value) => value.shutdown()
        case None => Future.successful(())
      }
    } yield ()
  }.toJavaCompletable

  @JsonNotification("exit")
  def exit(): Unit = {
    sys.exit(0)
  }

  @JsonNotification("textDocument/didOpen")
  def didOpen(params: DidOpenTextDocumentParams): CompletableFuture[Unit] = {
    val path = params.getTextDocument.getUri.toAbsolutePath
    openTextDocument.set(path)
    // Update md5 fingerprint from file contents on disk
    fingerprints.add(path, FileIO.slurp(path, charset))
    // Update in-memory buffer contents from LSP client
    buffers.put(path, params.getTextDocument.getText)
    if (path.isDependencySource(workspace)) {
      CompletableFutures.computeAsync { _ =>
        compilers.textDocument(path)
        ()
      }
    } else {
      compileSourceFiles(List(path)).toJavaCompletable
    }
  }

  @JsonNotification("textDocument/didChange")
  def didChange(
      params: DidChangeTextDocumentParams
  ): CompletableFuture[Unit] = {
    CompletableFuture.completedFuture {
      params.getContentChanges.asScala.headOption.foreach { change =>
        buffers.put(
          params.getTextDocument.getUri.toAbsolutePath,
          change.getText
        )
      }
    }
  }

  @JsonNotification("textDocument/didClose")
  def didClose(params: DidCloseTextDocumentParams): Unit = {
    buffers.remove(params.getTextDocument.getUri.toAbsolutePath)
  }

  @JsonNotification("metals/didFocusTextDocument")
  def didFocus(uri: String): CompletableFuture[Unit] = {
    compileSourceFiles(List(uri.toAbsolutePath)).toJavaCompletable
  }

  @JsonNotification("textDocument/didSave")
  def didSave(params: DidSaveTextDocumentParams): CompletableFuture[Unit] = {
    val path = params.getTextDocument.getUri.toAbsolutePath
    buffers.remove(path)
    onChange(List(path))
  }

  @JsonNotification("workspace/didChangeConfiguration")
  def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = {
    // TODO(olafur): Handle notification changes.
  }

  @JsonNotification("workspace/didChangeWatchedFiles")
  def didChangeWatchedFiles(
      params: DidChangeWatchedFilesParams
  ): CompletableFuture[Unit] = {
    onChange(params.getChanges.asScala.map(_.getUri.toAbsolutePath))
  }

  private def onChange(paths: Seq[AbsolutePath]): CompletableFuture[Unit] = {
    Future
      .sequence(
        List(
          compileSourceFiles(paths).ignoreValue,
          onSbtBuildChanged(paths).ignoreValue
        )
      )
      .ignoreValue
      .toJavaCompletable
  }

  @JsonRequest("textDocument/definition")
  def definition(
      position: TextDocumentPositionParams
  ): CompletableFuture[util.List[Location]] =
    CompletableFutures.computeAsync { _ =>
      definitionResult(position).locations
    }

  @JsonRequest("workspace/executeCommand")
  def executeCommand(params: ExecuteCommandParams): CompletableFuture[Unit] =
    params.getCommand match {
      case Commands.SCAN_WORSPACE_SOURCES =>
        indexWorkspaceScalaSources().toJavaCompletable
      case Commands.IMPORT_BUILD =>
        slowConnectToBuildServer(forceImport = true).toJavaUnitCompletable
      case Commands.RECONNECT_BUILD =>
        quickConnectToBuildServer().toJavaUnitCompletable
      case els =>
        scribe.error(s"Unknown command '$els'")
        CompletableFuture.completedFuture(())
    }

  private def slowConnectToBuildServer(
      forceImport: Boolean
  ): Future[BuildChange] = {
    for {
      result <- BuildServerConnection.reimportIfChanged(
        buildTools,
        workspace,
        languageClient,
        bloopInstall,
        forceImport
      )
      change <- {
        if (result.isInstalled) quickConnectToBuildServer()
        else if (result.isFailed) {
          if (workspace.resolve(".bloop").isDirectory) {
            languageClient.showMessage(Messages.ImportProjectPartiallyFailed)
            // Connect nevertheless, many build import failures are caused
            // by resolution errors in one weird module while other modules
            // exported successfully.
            quickConnectToBuildServer()
          } else {
            languageClient.showMessage(Messages.ImportProjectFailed)
            Future.successful(BuildChange.Failed)
          }
        } else {
          Future.successful(BuildChange.None)
        }
      }
    } yield {
      change
    }
  }

  private def quickConnectToBuildServer(): Future[BuildChange] = {
    if (!buildTools.isBloop) {
      Future.successful(BuildChange.None)
    } else {
      for {
        build <- timed("connected to build server") {
          bloopServer.connect(buildClient)
        }
        _ = {
          cancelables.add(build)
          buildServer.foreach(_.shutdown())
          buildServer = Some(build)
        }
        _ <- build.initialize()
        _ <- installWorkspaceBuildTargets(build)
          .trackInStatusBar("$(sync) importing workspace")
        _ = statusBar.addMessage("$(rocket) Imported workspace!")
        _ <- compileSourceFiles(buffers.open.toSeq)
      } yield BuildChange.Reconnected
    }
  }.recover {
    case NonFatal(e) =>
      val message =
        "Failed to connect with build server, no functionality will work."
      val details = " See logs for more details."
      scribe.error(message, e)
      languageClient.showMessage(
        new MessageParams(MessageType.Error, message + details)
      )
      BuildChange.Failed
  }

  /**
   * Visit every file and directory in the workspace and register
   * toplevel definitions for scala source files.
   */
  private def indexWorkspaceScalaSources(): Future[Unit] = Future {
    Files.walkFileTree(
      workspace.toNIO,
      new SimpleFileVisitor[Path] {
        override def visitFile(
            file: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          val path = AbsolutePath(file)
          if (path.toLanguage.isScala) {
            index.addSourceFile(path, None)
          }
          super.visitFile(file, attrs)
        }
        override def preVisitDirectory(
            dir: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          val path = AbsolutePath(dir)
          if (path.resolve("META-INF").isDirectory) {
            FileVisitResult.SKIP_SUBTREE
          } else if (dir.endsWith(".bloop") || dir.endsWith(".metals")) {
            FileVisitResult.SKIP_SUBTREE
          } else {
            super.preVisitDirectory(dir, attrs)
          }
        }
      }
    )
  }

  private def timed[T](didWhat: String)(thunk: => Future[T]): Future[T] = {
    val elapsed = new Timer(time)
    val result = thunk
    result.onComplete(_ => scribe.info(s"time: $didWhat in $elapsed"))
    result
  }

  /**
   * Index all build targets in the workspace.
   */
  private def installWorkspaceBuildTargets(
      build: BuildServerConnection
  ): Future[Unit] = timed("imported workspace") {
    for {
      workspaceBuildTargets <- build.server.workspaceBuildTargets().toScala
      _ = buildTargets.addWorkspaceBuildTargets(workspaceBuildTargets)
      _ = compilers.reset()
      ids = workspaceBuildTargets.getTargets.map(_.getId)
      scalacOptions <- build.server
        .buildTargetScalacOptions(new ScalacOptionsParams(ids))
        .toScala
      _ = {
        buildTargets.addScalacOptions(scalacOptions)
        JdkSources().foreach(zip => index.addSourceJar(zip))
      }
      dependencySources <- build.server
        .buildTargetDependencySources(new DependencySourcesParams(ids))
        .toScala
    } yield {
      for {
        item <- dependencySources.getItems.asScala
        sourceUri <- Option(item.getSources).toList.flatMap(_.asScala)
      } {
        try {
          val path = sourceUri.toAbsolutePath
          if (path.isJar) {
            // TODO(olafur): use `buildTarget/sources` once bloop upgrades to BSP v2
            index.addSourceJar(path)
          } else if (path.isDirectory) {
            buildTargets.addSourceDirectory(path, item.getTarget)
            FileIO
              .listAllFilesRecursively(path)
              .iterator
              .filter(_.toLanguage.isJava)
              .foreach(javaFile => index.addSourceFile(javaFile, Some(path)))
          }
        } catch {
          case NonFatal(e) =>
            scribe.error(s"error processing $sourceUri", e)
        }
      }
    }
  }

  private def reloadAndCompile(
      changedFiles: Seq[AbsolutePath]
  ): Future[Unit] = {
    for {
      change <- onSbtBuildChanged(changedFiles)
      _ <- {
        if (change.isReconnected) Future.successful(())
        else compileSourceFiles(changedFiles)
      }
    } yield ()
  }

  private val compileSourceFiles =
    new BatchedFunction[AbsolutePath, Unit](compileSourceFilesUnbatched)
  private def compileSourceFilesUnbatched(
      paths: Seq[AbsolutePath]
  ): Future[Unit] = {
    val scalaPaths = paths.filter(_.isScalaOrJava)
    buildServer match {
      case Some(build) if scalaPaths.nonEmpty =>
        val targets = scalaPaths.flatMap(buildTargets.inverseSources).distinct
        if (targets.isEmpty) {
          scribe.warn(s"no build target: ${scalaPaths.mkString("\n  ")}")
          Future.successful(())
        } else {
          val params = new CompileParams(targets.asJava)
          val name =
            targets.headOption
              .flatMap(buildTargets.info)
              .map(info => " " + info.getDisplayName)
              .getOrElse("")
          params.setArguments(new JsonArray)
          for {
            isSuccess <- build.server
              .buildTargetCompile(params)
              .toScala
              .trackInStatusBar(s"$$(sync) Compiling$name", maxDots = 5)
              .ignoreValue
              .map(_ => true)
              .recover {
                // FIXME: remove once bloop ugrades to BSP v2
                case _ => false
              }
          } yield {
            if (!isSuccess) {
              statusBar.addMessage(
                MetalsStatusParams(
                  "$(alert) Compile error",
                  command = Commands.OPEN_PROBLEMS
                )
              )
            }
          }
        }
      case _ =>
        Future.successful(())
    }
  }

  /**
   * Re-imports the sbt build if build files have changed.
   */
  private val onSbtBuildChanged =
    new BatchedFunction[AbsolutePath, BuildChange](onSbtBuildChangedUnbatched)
  private def onSbtBuildChangedUnbatched(
      paths: Seq[AbsolutePath]
  ): Future[BuildChange] = {
    val isBuildChange = paths.exists { path =>
      val project = workspace.resolve("project").toNIO
      val parent = path.toNIO.getParent
      val isBuildFile = parent == workspace.toNIO || parent == project
      val isSbtOrScala = path.isSbtOrScala
      isBuildFile && isSbtOrScala
    }
    val result: Future[BuildChange] =
      if (isBuildChange) {
        slowConnectToBuildServer(forceImport = false)
      } else {
        Future.successful(BuildChange.None)
      }
    result
  }

  /**
   * Returns textDocument/definition in addition to the resolved symbol.
   *
   * The resolved symbol is used for testing purposes only.
   */
  def definitionResult(
      position: TextDocumentPositionParams
  ): DefinitionResult = {
    val original = position.getTextDocument.getUri.toAbsolutePath
    if (original.toLanguage.isScala) {
      val result = DefinitionProvider.definition(
        original,
        position,
        workspace,
        mtags,
        buffers,
        index,
        textDocuments,
        config
      )
      for {
        destination <- result.definition
        if destination.isDependencySource(workspace)
      } {
        compilers.newDependencySourceFile(original, destination)
      }
      result
    } else {
      // Ignore non-scala files.
      DefinitionResult.empty
    }
  }

  private def newSymbolIndex(): OnDemandSymbolIndex = {
    OnDemandSymbolIndex(onError = {
      case e: ParseException =>
        scribe.error(e.toString())
      case NonFatal(e) =>
        scribe.error("unexpected error during source scanning", e)
    })
  }

}
