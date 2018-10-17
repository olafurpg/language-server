package scala.meta.internal.metals

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j.CompileReport
import java.util
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import scala.collection.concurrent.TrieMap
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath

class Diagnostics(bsp: BuildTargets, languageClient: LanguageClient) {
  private val diagnostics =
    TrieMap.empty[AbsolutePath, util.Queue[Diagnostic]]
  private val hasPublishedDiagnostics =
    Collections
      .newSetFromMap(new ConcurrentHashMap[AbsolutePath, java.lang.Boolean]())
      .asScala

  def onBuildPublishDiagnostics(
      params: bsp4j.PublishDiagnosticsParams
  ): Unit = {
    if (params.getDiagnostics.isEmpty) {
      () // Do nothing.
    } else {
      val path = params.getUri.toAbsolutePath
      val queue =
        diagnostics.getOrElseUpdate(
          path,
          new ConcurrentLinkedQueue[Diagnostic]()
        )
      params.getDiagnostics.forEach { buildDiagnostic =>
        queue.add(buildDiagnostic.toLSP)
      }
      languageClient.publishDiagnostics(
        new PublishDiagnosticsParams(params.getUri, new util.ArrayList(queue))
      )
    }
  }

  def onBuildTargetCompileReport(params: CompileReport): Unit = {
    hasPublishedDiagnostics ++= diagnostics.keysIterator
    for {
      textDocument <- hasPublishedDiagnostics
      if bsp.inverseSources(textDocument).contains(params.getTarget)
    } {
      val documentDiagnostics = diagnostics.remove(textDocument)
      if (documentDiagnostics.isEmpty) {
        // Send empty diagnostics, this is necessary to work around https://github.com/scalacenter/bloop/issues/692
        val params = new PublishDiagnosticsParams(
          textDocument.toURI.toString,
          Collections.emptyList()
        )
        hasPublishedDiagnostics.remove(textDocument)
        languageClient.publishDiagnostics(params)
      } else {
        documentDiagnostics.foreach(_.clear())
      }
    }
  }
}

