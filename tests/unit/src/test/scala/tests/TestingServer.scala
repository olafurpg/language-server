package tests

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.Future
import scala.meta.internal.io.PathIO
import scala.meta.internal.metals.Debug
import scala.meta.internal.metals.{Buffers, MetalsLanguageClient, MetalsLanguageServer}
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.internal.semanticdb.Scala.Symbols
import scala.meta.io.AbsolutePath
import scala.meta.io.RelativePath
import scala.meta.testkit.StringFS
import scala.meta.tokens.Token
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.{semanticdb => s}
import scala.meta.internal.semanticdb.Scala._

class TestingServer(
    client: MetalsLanguageClient,
    root: AbsolutePath,
    buffers: Buffers
)(
    implicit ex: ExecutionContextExecutorService
) {
  val server = new MetalsLanguageServer(
    ex,
    buffers = buffers,
    redirectSystemOut = false
  )
  server.connectToLanguageClient(client)

  private def write(layout: String): Unit = {
    StringFS.fromString(layout, root = root)
  }

  def initialize(layout: String): Future[Unit] = {
    Debug.printEnclosing()
    cleanUnmanagedFiles()
    write(layout)
    val params = new InitializeParams
    params.setRootUri(root.toURI.toString)
    for {
      _ <- server.initialize(params).toScala
      _ <- server.initialized(new InitializedParams).toScala
    } yield ()
  }
  private def toPath(filename: String): AbsolutePath = {
    val path = RelativePath(filename)
    val abspath = root.resolve(path)
    require(abspath.isFile, s"no such file: $abspath")
    abspath
  }

  def executeCommand(command: String): Future[Unit] = {
    Debug.printEnclosing()
    server
      .executeCommand(
        new ExecuteCommandParams(command, Collections.emptyList())
      )
      .toScala
  }
  def didSave(filename: String)(fn: String => String): Future[Unit] = {
    Debug.printEnclosing()
    val abspath = toPath(filename)
    val oldText = abspath.toInputFromBuffers(buffers).text
    val newText = fn(oldText)
    Files.write(
      abspath.toNIO,
      newText.getBytes(StandardCharsets.UTF_8)
    )
    val first = server.didSave(
      new DidSaveTextDocumentParams(
        new TextDocumentIdentifier(abspath.toURI.toString),
      )
    )
    Thread.sleep(50) // mimic delay for file watching
    val second = server.didChangeWatchedFiles(
      new DidChangeWatchedFilesParams(
        Collections.singletonList(
          new FileEvent(
            abspath.toURI.toString,
            FileChangeType.Changed
          )
        )
      )
    )
    Future.sequence(List(first.toScala, second.toScala)).ignoreValue
  }

  def didChange(filename: String)(fn: String => String): Future[Unit] = {
    Debug.printEnclosing()
    val abspath = toPath(filename)
    val oldText = abspath.readText
    val newText = fn(oldText)
    server
      .didChange(
        new DidChangeTextDocumentParams(
          new VersionedTextDocumentIdentifier(abspath.toURI.toString, 0),
          Collections.singletonList(new TextDocumentContentChangeEvent(newText))
        )
      )
      .toScala
  }

  def didOpen(filename: String): Future[Unit] = {
    Debug.printEnclosing()
    val abspath = toPath(filename)
    val uri = abspath.toURI.toString
    val extension = PathIO.extension(abspath.toNIO)
    val text = abspath.readText
    server
      .didOpen(
        new DidOpenTextDocumentParams(
          new TextDocumentItem(uri, extension, 0, text)
        )
      )
      .toScala
  }

  private def toSemanticdbTextDocument(path: AbsolutePath): s.TextDocument = {
    val input = path.toInputFromBuffers(buffers)
    val identifier = path.toTextDocumentIdentifier
    val occurrences = ListBuffer.empty[s.SymbolOccurrence]
    input.tokenize.get.foreach { token =>
      val range = token.pos.toLSP
      val start = range.getStart
      val params = new TextDocumentPositionParams(identifier, start)
      val definition = server.definitionResult(params)
      val locations = definition.locations.asScala.toList
      val symbols = locations.map { location =>
        val isSameFile = identifier.getUri == location.getUri
        if (isSameFile) {
          s"L${location.getRange.getStart.getLine}"
        } else {
          val filename = location.getUri.toAbsolutePath.toNIO.getFileName
          s"$filename:${location.getRange.getStart.getLine}"
        }
      }
      val occurrence = token match {
        case _: Token.Ident | _: Token.Interpolation.Id =>
          if (definition.symbol.isPackage) None // ignore packages
          else if (symbols.isEmpty) Some("<no symbol>")
          else Some(Symbols.Multi(symbols))
        case _ =>
          if (symbols.isEmpty) None // OK, expected
          else Some(s"unexpected: ${Symbols.Multi(symbols)}")
      }
      occurrences ++= occurrence.map { symbol =>
        s.SymbolOccurrence(Some(token.pos.toSemanticdb), symbol)
      }
    }
    s.TextDocument(
      schema = s.Schema.SEMANTICDB4,
      uri = input.path,
      text = input.text,
      occurrences = occurrences
    )
  }

  def workspaceDefinitions: String = {
    buffers.open.toSeq
      .sortBy(_.toURI.toString)
      .map { path =>
        val textDocument = toSemanticdbTextDocument(path)
        val relpath = path.toRelative(root).toURI(isDirectory = false).toString
        val printedTextDocument = Semanticdbs.printTextDocument(textDocument)
        s"/$relpath\n$printedTextDocument"
      }
      .mkString("\n")
  }

  def cancel(): Unit = {
    server.cancel()
  }

  def cleanUnmanagedFiles(): Unit = {
    Files.walkFileTree(
      root.toNIO,
      new SimpleFileVisitor[Path] {
        override def visitFile(
            file: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          PathIO.extension(file) match {
            case "json" if file.getParent.endsWith(".bloop") =>
            case _ =>
              Files.delete(file)
          }
          super.visitFile(file, attrs)
        }
        override def postVisitDirectory(
            dir: Path,
            exc: IOException
        ): FileVisitResult = {
          val isEmpty = !Files.list(dir).iterator().hasNext
          if (isEmpty) {
            Files.delete(dir)
          }
          super.postVisitDirectory(dir, exc)
        }
        override def preVisitDirectory(
            dir: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          if (dir.endsWith(".metals"))
            FileVisitResult.SKIP_SUBTREE
          else super.preVisitDirectory(dir, attrs)
        }
      }
    )
  }
}
