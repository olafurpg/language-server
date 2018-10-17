package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.BuildTarget
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.ScalacOptionsItem
import ch.epfl.scala.bsp4j.ScalacOptionsResult
import ch.epfl.scala.bsp4j.WorkspaceBuildTargetsResult
import scala.collection.concurrent.TrieMap
import scala.meta.io.AbsolutePath
import MetalsEnrichments._
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentLinkedQueue
import scala.meta.internal.mtags.Md5Fingerprints
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.internal.mtags.TextDocumentLookup
import scala.meta.internal.mtags.TextDocuments
import scala.meta.internal.mtags.MtagsEnrichments._

final class BuildTargets(
    buffers: Buffers,
    charset: Charset,
    workspace: AbsolutePath,
    fingerprints: Md5Fingerprints
) extends TextDocuments {
  private val sourceDirectoriesToBuildTarget =
    TrieMap.empty[AbsolutePath, ConcurrentLinkedQueue[BuildTargetIdentifier]]
  private val buildTargetInfo =
    TrieMap.empty[BuildTargetIdentifier, BuildTarget]
  private val scalacTargetInfo =
    TrieMap.empty[BuildTargetIdentifier, ScalacOptionsItem]

  def reset(): Unit = {
    sourceDirectoriesToBuildTarget.values.foreach(_.clear())
    sourceDirectoriesToBuildTarget.clear()
    buildTargetInfo.clear()
    scalacTargetInfo.clear()
  }

  def addSourceDirectory(
      directory: AbsolutePath,
      buildTarget: BuildTargetIdentifier
  ): Unit = {
    val queue = sourceDirectoriesToBuildTarget.getOrElseUpdate(
      directory,
      new ConcurrentLinkedQueue()
    )
    queue.add(buildTarget)
  }

  def addWorkspaceBuildTargets(result: WorkspaceBuildTargetsResult): Unit = {
    result.getTargets.asScala.foreach { target =>
      buildTargetInfo(target.getId) = target
    }
  }

  def addScalacOptions(result: ScalacOptionsResult): Unit = {
    result.getItems.asScala.foreach { item =>
      scalacTargetInfo(item.getTarget) = item
    }
  }

  def info(
      buildTarget: BuildTargetIdentifier
  ): Option[BuildTarget] =
    buildTargetInfo.get(buildTarget)

  def scalacOptions(
      buildTarget: BuildTargetIdentifier
  ): Option[ScalacOptionsItem] =
    scalacTargetInfo.get(buildTarget)

  /**
   * Returns the source files and directories for this build target.
   */
  def sources(buildTarget: BuildTargetIdentifier): List[AbsolutePath] = {
    for {
      open <- buffers.open
      target <- inverseSources(open)
      if target == buildTarget
    } yield open
  }.toList

  /**
   * Returns the first build target containing this source file.
   */
  def inverseSources(
      textDocument: AbsolutePath
  ): Option[BuildTargetIdentifier] = {
    for {
      buildTargets <- sourceDirectoriesToBuildTarget.collectFirst {
        case (sourceDirectory, buildTargets)
            if textDocument.toNIO.startsWith(sourceDirectory.toNIO) =>
          buildTargets.asScala
      }
      target <- buildTargets // prioritize JVM targets over JS/Native
        .find(x => scalacOptions(x).exists(_.isJVM))
        .orElse(buildTargets.headOption)
    } yield target
  }

  /**
   * Returns the SemanticDB document for this source file.
   */
  override def textDocument(file: AbsolutePath): TextDocumentLookup = {
    if (!file.toLanguage.isScala ||
      file.toNIO.getFileSystem != workspace.toNIO.getFileSystem) {
      TextDocumentLookup.NotFound(file)
    } else {
      semanticdbTargetroot(file) match {
        case Some(targetroot) =>
          Semanticdbs.loadTextDocument(
            file,
            workspace,
            charset,
            fingerprints,
            semanticdbRelativePath => {
              val semanticdbpath = targetroot.resolve(semanticdbRelativePath)
              if (semanticdbpath.isFile) Some(semanticdbpath)
              else None
            }
          )
        case None =>
          TextDocumentLookup.NotFound(file)
      }
    }
  }

  /**
   * Returns the directory containing SemanticDB files for this Scala source file.
   */
  private def semanticdbTargetroot(
      scalaPath: AbsolutePath
  ): Option[AbsolutePath] = {
    for {
      buildTarget <- inverseSources(scalaPath)
      scalacOptions <- scalacTargetInfo.get(buildTarget)
    } yield {
      scalacOptions
        .semanticdbFlag("targetroot")
        .map(AbsolutePath(_))
        .getOrElse(scalacOptions.getClassDirectory.toAbsolutePath)
    }
  }

}
