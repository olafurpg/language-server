package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.ScalacOptionsItem
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import scala.collection.concurrent.TrieMap
import scala.meta.interactive.InteractiveSemanticdb
import scala.meta.internal.io.FileIO
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.mtags.TextDocumentLookup
import scala.meta.internal.mtags.TextDocuments
import scala.meta.internal.tokenizers.PlatformTokenizerCache
import scala.meta.io.AbsolutePath
import scala.tools.nsc.interactive.Global
import scala.meta.internal.{semanticdb => s}

final class Compilers(
    workspace: AbsolutePath,
    buildTargets: BuildTargets,
    charset: Charset
) extends Cancelable
    with TextDocuments {
  private val globalCache = TrieMap.empty[AbsolutePath, Global]
  private val textDocumentCache =
    TrieMap.empty[AbsolutePath, s.TextDocument]
  private case class SourcePair(
      original: AbsolutePath,
      destination: AbsolutePath
  )
  private val todos = new ConcurrentLinkedQueue[SourcePair]()
  private val lock = new Object

  def newDependencySourceFile(
      original: AbsolutePath,
      destination: AbsolutePath
  ): Unit = {
    todos.add(SourcePair(original, destination))
  }

  def reset(): Unit = {
    textDocumentCache.clear()
    globalCache.values.foreach(_.askShutdown())
    globalCache.clear()
  }
  override def cancel(): Unit = {
    reset()
  }

  override def toString: String = s"Compilers(${globalCache.keySet})"

  override def textDocument(source: AbsolutePath): TextDocumentLookup = {
    if (!source.toLanguage.isScala ||
      !source.isDependencySource(workspace)) {
      TextDocumentLookup.NotFound(source)
    } else if (textDocumentCache.contains(source)) {
      TextDocumentLookup.Success(textDocumentCache(source))
    } else {
      flushTodo()
      val result = for {
        global <- globalCache.get(source)
      } yield {
        val filename = source.toString()
        val text = FileIO.slurp(source, charset)
        val textDocument = InteractiveSemanticdb.toTextDocument(
          global,
          code = text,
          filename = filename,
          timeout = TimeUnit.SECONDS.toMillis(15),
          options = List(
            "-P:semanticdb:symbols:none",
            "-P:semanticdb:text:on",
          )
        )
        textDocumentCache.put(source, textDocument)
        PlatformTokenizerCache.megaCache.clear() // :facepalm:
        textDocument
      }
      TextDocumentLookup.fromOption(source, result)
    }
  }

  private def newGlobal(item: ScalacOptionsItem): Global = {
    val classpath = item.getClasspath.asScala.iterator
      .map(uri => Paths.get(URI.create(uri)))
      .filterNot(path => Files.isDirectory(path))
      .mkString(java.io.File.pathSeparator)
    val scalacOptions = item.getOptions.asScala.iterator
      .filterNot(_.isNonJVMPlatformOption)
      .toList
    InteractiveSemanticdb.newCompiler(classpath, scalacOptions)
  }

  private def flushTodo(): Unit = lock.synchronized {
    todos.asScala.foreach { source =>
      globalCache.get(source.destination) match {
        case Some(_) => () // nothing to do
        case None =>
          globalCache.get(source.original) match {
            case Some(global) =>
              globalCache.put(source.destination, global)
            case None =>
              for {
                buildTarget <- buildTargets.inverseSources(source.original)
                info <- buildTargets.info(buildTarget)
                scalaInfo <- info.asScalaBuildTarget
                isOk = scalaInfo.getScalaVersion.startsWith("2.12")
                if {
                  if (!isOk) {
                    scribe.warn(
                      s"unsupported scala version: ${scalaInfo.getScalaVersion}"
                    )
                  }
                  isOk
                }
                scalacOptions <- buildTargets.scalacOptions(buildTarget)
              } {
                val global = newGlobal(scalacOptions)
                globalCache.put(source.original, global)
                globalCache.put(source.destination, global)
              }
          }
      }
    }
    todos.clear()
  }

}
