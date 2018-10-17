package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.BuildClientCapabilities
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.DependencySourcesParams
import ch.epfl.scala.bsp4j.InitializeBuildParams
import com.google.gson.JsonArray
import java.net.URI
import java.nio.file.Paths
import java.util
import java.util.Collections
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.Future
import scala.meta.internal.metals.Messages.ImportProjectViaBloop
import scala.meta.internal.metals.Messages.ReimportSbtProject
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath

case class BuildServerConnection(
    workspace: AbsolutePath,
    client: MetalsBuildClient,
    server: MetalsBuildServer,
    cancelables: List[Cancelable]
)(implicit ec: ExecutionContext)
    extends Cancelable {
  def initialize(): Future[Unit] = {
    for {
      _ <- server
        .buildInitialize(
          new InitializeBuildParams(
            workspace.toURI.toString,
            new BuildClientCapabilities(
              Collections.singletonList("scala"),
              false
            )
          )
        )
        .toScala
    } yield {
      server.onBuildInitialized()
    }
  }
  def shutdown(): Future[Unit] = {
    for {
      _ <- server.buildShutdown().toScala
    } yield {
      server.onBuildExit()
      cancel()
    }
  }
  def allWorkspaceIds(): Future[util.List[BuildTargetIdentifier]] = {
    server.workspaceBuildTargets().toScala.map(_.getTargets.map(_.getId))(ec)
  }
  def allDependencySources(): Future[List[AbsolutePath]] = {
    for {
      ids <- allWorkspaceIds()
      _ = ids.forEach(id => id.setUri(URI.create(id.getUri).toString))
      compileParams = new CompileParams(ids)
      _ = compileParams.setArguments(new JsonArray)
      _ <- server.buildTargetCompile(compileParams).toScala
      sources <- server
        .buildTargetDependencySources(new DependencySourcesParams(ids))
        .toScala
      items = sources.getItems.asScala
    } yield {
      items.iterator
        .filter(_.getSources != null)
        .flatMap(_.getSources.asScala)
        .map(uri => AbsolutePath(Paths.get(URI.create(uri))))
        .toList
    }
  }
  override def cancel(): Unit = Cancelable.cancelAll(cancelables)
}

object BuildServerConnection {

  def requestImport(
      buildTools: BuildTools,
      languageClient: MetalsLanguageClient,
      forceImport: Boolean
  )(implicit ec: ExecutionContext): Future[Confirmation] = {
    if (forceImport) Future.successful(Confirmation.Yes)
    else if (buildTools.isBloop) {
      languageClient
        .showMessageRequest(ReimportSbtProject.params)
        .toScala
        .map { item =>
          Confirmation.fromBoolean(item == ReimportSbtProject.yes)
        }
    } else {
      languageClient
        .showMessageRequest(ImportProjectViaBloop.params)
        .toScala
        .map { item =>
          Confirmation.fromBoolean(item == ImportProjectViaBloop.yes)
        }
    }
  }

  def reimportIfChanged(
      buildTools: BuildTools,
      workspace: AbsolutePath,
      languageClient: MetalsLanguageClient,
      bloopInstall: BloopInstall,
      forceImport: Boolean
  )(
      implicit ec: ExecutionContextExecutorService
  ): Future[BloopInstallResult] = {
    for {
      sbt <- buildTools.asSbt
      if SbtVersion.isSupported(sbt.version)
      current <- SbtChecksum.current(workspace)
      persisted = SbtChecksum.persisted(workspace)
      if forceImport || persisted.isEmpty || !persisted.contains(current)
    } yield {
      for {
        userResponse <- requestImport(buildTools, languageClient, forceImport)
        installResult <- {
          if (userResponse.isYes) {
            bloopInstall.runAndPersistChecksum(current)
          } else {
            Future.successful(BloopInstallResult.Rejected)
          }
        }
      } yield installResult
    }
  }.getOrElse(Future.successful(BloopInstallResult.Unchanged))
}
