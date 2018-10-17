package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.ScalacOptionsItem
import ch.epfl.scala.{bsp4j => b}
import com.google.gson.Gson
import com.google.gson.JsonElement
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.{lsp4j => l}
import scala.collection.convert.DecorateAsJava
import scala.collection.convert.DecorateAsScala
import scala.compat.java8.FutureConverters
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.meta.inputs.Input
import scala.meta.internal.io.FileIO
import scala.meta.internal.mtags.MtagsEnrichments._
import scala.meta.internal.{semanticdb => s}
import scala.meta.io.AbsolutePath
import scala.util.Try
import scala.{meta => m}

object MetalsEnrichments extends DecorateAsJava with DecorateAsScala {

  implicit class FutureOps[T](future: Future[T]) {
    def toJava: CompletionStage[T] = FutureConverters.toJava(future)
    def toJavaCompletable: CompletableFuture[T] = toJava.toCompletableFuture
    def toJavaUnitCompletable(
        implicit ec: ExecutionContext
    ): CompletableFuture[Unit] =
      future.ignoreValue.toJavaCompletable
  }

  implicit class CompletionStageOps[T](future: CompletionStage[T]) {
    def toScala: Future[T] = FutureConverters.toScala(future)
  }

  implicit class XtensionBuildTarget(buildTarget: b.BuildTarget) {
    def asScalaBuildTarget: Option[b.ScalaBuildTarget] = {
      for {
        data <- Option(buildTarget.getData)
        if data.isInstanceOf[JsonElement]
        info <- Try(
          new Gson().fromJson[b.ScalaBuildTarget](
            data.asInstanceOf[JsonElement],
            classOf[b.ScalaBuildTarget]
          )
        ).toOption
      } yield info
    }
  }

  implicit class XtensionEditDistance(result: Either[EmptyResult, m.Position]) {
    def foldResult[B](
        onPosition: m.Position => B,
        onUnchanged: () => B,
        onNoMatch: () => B
    ): B = result match {
      case Right(pos) => onPosition(pos)
      case Left(EmptyResult.Unchanged) => onUnchanged()
      case Left(EmptyResult.NoMatch) => onNoMatch()
    }
  }

  implicit class XtensionScalaFuture[A](fut: Future[A]) {
    def trackInStatusBar(
        message: String,
        maxDots: Int = Int.MaxValue
    )(implicit statusBar: StatusBar): Future[A] = {
      statusBar.addFuture(message, fut, maxDots = maxDots)
      fut
    }
    def ignoreValue(implicit ec: ExecutionContext): Future[Unit] =
      fut.map(_ => ())
    def logError(
        doingWhat: String
    )(implicit ec: ExecutionContext): Future[A] = {
      fut.recover {
        case e =>
          scribe.error(s"Unexpected error while $doingWhat", e)
          throw e
      }
    }
    def get(duration: Duration = Duration("10s")): A = {
      Await.result(fut, duration)
    }
  }

  implicit class XtensionJavaList[A](lst: util.List[A]) {
    def map[B](fn: A => B): util.List[B] = {
      val out = new util.ArrayList[B]()
      val iter = lst.iterator()
      while (iter.hasNext) {
        out.add(fn(iter.next()))
      }
      out
    }
  }

  implicit class XtensionPositionLsp(pos: m.Position) {
    def toSemanticdb: s.Range = {
      new s.Range(
        pos.startLine,
        pos.startColumn,
        pos.endLine,
        pos.endColumn
      )
    }
    def toLSP: l.Range = {
      new l.Range(
        new l.Position(pos.startLine, pos.startColumn),
        new l.Position(pos.endLine, pos.endColumn)
      )
    }
  }

  implicit class XtensionAbsolutePathBuffers(path: AbsolutePath) {
    def isDependencySource(workspace: AbsolutePath): Boolean =
      workspace.toNIO.getFileSystem == path.toNIO.getFileSystem &&
        path.toNIO.startsWith(
          workspace.resolve(DefinitionProvider.DependencySource).toNIO
        )
    def toFileOnDisk(
        workspace: AbsolutePath,
        config: MetalsServerConfig
    ): AbsolutePath = {
      if (path.toNIO.getFileSystem == workspace.toNIO.getFileSystem) {
        path
      } else {
        val dependencySource =
          workspace.resolve(DefinitionProvider.DependencySource)
        Files.createDirectories(dependencySource.toNIO)
        val out =
          dependencySource.toNIO.resolve(path.toNIO.getFileName.toString)
        Files.copy(path.toNIO, out, StandardCopyOption.REPLACE_EXISTING)
        out.toFile.setReadOnly()
        AbsolutePath(out)
      }
    }
    def toTextDocumentIdentifier: TextDocumentIdentifier = {
      new TextDocumentIdentifier(path.toURI.toString)
    }
    def readText: String = {
      FileIO.slurp(path, StandardCharsets.UTF_8)
    }
    def isJar: Boolean = {
      val filename = path.toNIO.getFileName.toString
      filename.endsWith(".jar")
    }
    def isSbtOrScala: Boolean = {
      val filename = path.toNIO.getFileName.toString
      filename.endsWith(".sbt") ||
      filename.endsWith(".scala")
    }
    def toInputFromBuffers(buffers: Buffers): Input.VirtualFile = {
      buffers.get(path) match {
        case Some(text) => Input.VirtualFile(path.toString(), text)
        case None => path.toInput
      }
    }
  }

  implicit class XtensionStringUriProtocol(value: String) {

    def isNonJVMPlatformOption: Boolean = {
      def isCompilerPlugin(name: String, organization: String): Boolean = {
        value.startsWith("-Xplugin:") &&
        value.contains(name) &&
        value.contains(organization)
      }
      // Scala Native and Scala.js are not needed to navigate dependency sources
      isCompilerPlugin("nscplugin", "scala-native") ||
      isCompilerPlugin("scalajs-compiler", "scala-js") ||
      value.startsWith("-P:scalajs:")
    }

    def toAbsolutePath: AbsolutePath =
      AbsolutePath(Paths.get(URI.create(value.stripPrefix("metals:"))))
  }

  implicit class XtensionTextDocumentSemanticdb(textDocument: s.TextDocument) {
    def toInput: Input = Input.VirtualFile(textDocument.uri, textDocument.text)
    def definition(uri: String, symbol: String): Option[l.Location] = {
      textDocument.occurrences
        .find(o => o.role.isDefinition && o.symbol == symbol)
        .map { occ =>
          occ.toLocation(uri)
        }
    }
  }

  implicit class XtensionSeverityBsp(sev: b.DiagnosticSeverity) {
    def toLSP: l.DiagnosticSeverity =
      l.DiagnosticSeverity.forValue(sev.getValue)
  }

  implicit class XtensionPositionBSp(pos: b.Position) {
    def toLSP: l.Position =
      // FIXME: remove -1 after https://github.com/scalacenter/bloop/issues/691
      new l.Position(pos.getLine - 1, pos.getCharacter)
  }

  implicit class XtensionRangeBsp(range: b.Range) {
    def toLSP: l.Range =
      new l.Range(range.getStart.toLSP, range.getEnd.toLSP)
  }

  implicit class XtensionRangeLanguageProtocol(range: l.Range) {
    def toLineColumn: String = {
      val sb = new StringBuilder()
        .append(range.getStart.getLine)
        .append(":")
        .append(range.getStart.getCharacter)
      if (range.getEnd != range.getStart) {
        sb.append("-")
          .append(range.getEnd.getLine)
          .append(":")
          .append(range.getEnd.getCharacter)
      }
      sb.toString()
    }
  }

  implicit class XtensionRangeBuildProtocol(range: s.Range) {
    def toLSP: l.Range = {
      val start = new l.Position(range.startLine, range.startCharacter)
      val end = new l.Position(range.endLine, range.endCharacter)
      new l.Range(start, end)
    }
    def encloses(other: l.Position): Boolean = {
      range.startLine <= other.getLine &&
      range.endLine >= other.getLine &&
      range.startCharacter <= other.getCharacter &&
      range.endCharacter > other.getCharacter
    }
    def encloses(other: l.Range): Boolean = {
      encloses(other.getStart) &&
      encloses(other.getEnd)
    }
  }

  implicit class XtensionSymbolOccurrenceProtocol(occ: s.SymbolOccurrence) {
    def toLocation(uri: String): l.Location = {
      val range = occ.range.getOrElse(s.Range(0, 0, 0, 0)).toLSP
      new l.Location(uri, range)
    }
    def encloses(pos: l.Position): Boolean =
      occ.range.isDefined &&
        occ.range.get.encloses(pos)
  }

  implicit class XtensionDiagnosticBsp(diag: b.Diagnostic) {
    def toLSP: l.Diagnostic =
      new l.Diagnostic(
        diag.getRange.toLSP,
        diag.getMessage,
        diag.getSeverity.toLSP,
        diag.getSource,
        diag.getCode
      )
  }

  implicit class XtensionScalacOptions(item: b.ScalacOptionsItem) {
    def isJVM: Boolean = {
      !item.getOptions.asScala.exists(_.isNonJVMPlatformOption)
    }

    def semanticdbFlag(name: String): Option[String] = {
      val flag = s"-P:semanticdb:$name:"
      item.getOptions.asScala
        .find(_.startsWith(flag))
        .map(_.stripPrefix(flag))
    }
  }

}
