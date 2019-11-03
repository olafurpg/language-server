package scala.meta.internal.worksheets

import scala.meta._
import scala.meta.internal.decorations.DecorationOptions
import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.Buffers
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.Cancelable
import scala.collection.concurrent.TrieMap
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import mdoc.internal.cli.Context
import mdoc.internal.markdown.SectionInput
import mdoc.internal.markdown.Modifier
import mdoc.internal.markdown.Instrumenter
import mdoc.internal.markdown.MarkdownCompiler
import org.eclipse.{lsp4j => l}
import scala.meta.internal.decorations.ThemableDecorationInstanceRenderOptions
import scala.meta.internal.decorations.ThemableDecorationAttachmentRenderOptions
import mdoc.internal.cli.Settings
import scala.meta.internal.metals.ScalaTarget
import scala.concurrent.Future
import scala.meta.pc.CancelToken
import pprint.TPrintColors
import mdoc.document.Binder
import scala.meta.internal.metals.UserConfiguration
import scala.collection.immutable.Nil
import mdoc.document.Statement
import scala.meta.internal.metals.MetalsLanguageClient
import scala.meta.internal.metals.ScalaVersions
import scala.meta.internal.metals.BuildInfo
import org.eclipse.lsp4j.PublishDiagnosticsParams
import scala.meta.internal.pc.CompilerJobQueue
import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext
import java.util.concurrent.ScheduledExecutorService
import scala.meta.internal.metals.MetalsSlowTaskParams
import java.util.concurrent.TimeUnit
import scala.meta.internal.metals.MutableCancelable
import scala.meta.internal.metals.StatusBar

class MetalsWorksheetProvider(
    workspace: AbsolutePath,
    buffers: Buffers,
    buildTargets: BuildTargets,
    languageClient: MetalsLanguageClient,
    userConfig: () => UserConfiguration,
    sh: ScheduledExecutorService,
    statusBar: StatusBar
)(implicit ec: ExecutionContext)
    extends WorksheetProvider
    with Cancelable {
  private val jobs = CompilerJobQueue()
  private val cancelables = new MutableCancelable()
  private val contexts = new TrieMap[BuildTargetIdentifier, Context]()
  private val reporter = new StoreReporter()

  implicit class XtensionStatement(statement: Statement) {
    def isUnit: Boolean = statement.binders match {
      case head :: Nil => head.isUnit
      case _ => false
    }
  }
  implicit class XtensionBinder(binder: Binder[_]) {
    def isUnit: Boolean =
      binder.tpe.render(TPrintColors.BlackWhite).endsWith("Unit")
  }
  def clear(): Unit = {
    jobs.shutdown()
    contexts.values.foreach { context =>
      context.compiler.global.close()
    }
    contexts.clear()
  }
  def cancel(): Unit = {
    clear()
  }

  override def decorations(
      path: AbsolutePath,
      token: CancelToken
  ): Future[Array[DecorationOptions]] = {
    if (path.extension != "sc") {
      Future.successful(Array.empty)
    } else {
      reporter.reset()
      val result = new CompletableFuture[Array[DecorationOptions]]()
      cancelables.add(Cancelable(() => result.cancel(true)))
      token.onCancel().asScala.foreach {
        case java.lang.Boolean.TRUE => result.cancel(true)
        case _ =>
      }
      def runEvaluation(): Unit = {
        statusBar.trackFuture(
          s"Evaluting ${path.filename}",
          result.asScala,
          showTimer = true
        )
        token.checkCanceled()
        val thread = new Thread(s"Evaluating Worksheet ${path.filename}") {
          override def run(): Unit = {
            result.complete(evaluate(path, token))
          }
        }
        stopThreadOnCancel(path, result, thread)
        thread.start()
        thread.join()
      }
      jobs.submit(
        result,
        () => {
          try runEvaluation()
          catch {
            case _: Throwable =>
            // continue
          }
        }
      )
      result.asScala
    }
  }

  private def stopThreadOnCancel(
      path: AbsolutePath,
      result: CompletableFuture[_],
      thread: Thread
  ): Unit = {
    val stopThread = new Runnable {
      def run(): Unit = {
        if (thread.isAlive()) {
          scribe.warn(s"thread stop: ${thread.getName()}")
          Cancelable.cancelAll(
            List(
              Cancelable(() => thread.stop()),
              cancelables
            )
          )
        }
      }
    }
    val promptUserToCancel = new Runnable {
      def run(): Unit = {
        if (!result.isDone()) {
          val cancel = languageClient.metalsSlowTask(
            new MetalsSlowTaskParams(
              s"Evaluating worksheet '${path.filename}'",
              noLogs = true
            )
          )
          cancel.asScala.foreach { c =>
            if (c.cancel && thread.isAlive()) {
              sh.schedule(stopThread, 1, TimeUnit.SECONDS)
              scribe.warn(s"thread interrupt: ${thread.getName()}")
              thread.interrupt()
            }
          }
          result.asScala.onComplete(_ => cancel.cancel(true))
        }
      }
    }
    sh.schedule(promptUserToCancel, 2, TimeUnit.SECONDS)
  }
  def evaluate(
      path: AbsolutePath,
      token: CancelToken
  ): Array[DecorationOptions] = {
    val commentHeader = " // "
    val input = path.toInputFromBuffers(buffers)
    val decorations = for {
      ctx <- getContext(path)
      source <- dialects.Sbt1(input).parse[Source].toOption
    } yield {
      val sectionInput = SectionInput(
        path.toInputFromBuffers(buffers),
        source,
        Modifier.Default()
      )
      val sectionInputs = List(sectionInput)
      val instrumented = Instrumenter.instrument(sectionInputs)
      val rendered = MarkdownCompiler.buildDocument(
        ctx.compiler,
        ctx.reporter,
        sectionInputs,
        instrumented,
        path.toString
      )
      val decorations = for {
        section <- rendered.sections.iterator
        statement <- section.section.statements
      } yield {
        val pos = statement.position
        val range = new l.Range(
          new l.Position(pos.startLine, pos.startColumn),
          new l.Position(pos.endLine, pos.endColumn)
        )
        val margin = math.max(
          20,
          userConfig().screenWidth - statement.position.endColumn
        )
        val isEmptyValue = statement.isUnit || statement.binders.isEmpty
        val contentText: String = Iterator[Iterator[Char]](
          commentHeader.iterator, {
            if (isEmptyValue) {
              if (statement.out.isEmpty()) "".iterator
              else statement.out.linesIterator.next().toCharArray().iterator
            } else {
              val isSingle = statement.binders.lengthCompare(1) == 0
              for {
                (binder, i) <- statement.binders.iterator.zipWithIndex
                text <- Iterator[Iterator[Char]](
                  if (isSingle) List.empty[Char].iterator
                  else {
                    val comma = if (i == 0) "" else ", "
                    s"$comma${binder.name}=".iterator
                  },
                  pprint.PPrinter.BlackWhite
                    .tokenize(
                      binder.value,
                      width = margin
                    )
                    .map(_.getChars)
                    .filterNot(_.iterator.forall(_.isWhitespace))
                    .flatMap(_.iterator)
                    .filter {
                      case '\n' => false
                      case _ => true
                    }
                    .take(margin)
                )
                char <- text
              } yield char
            }
          }
        ).flatten.mkString
        val hoverMessage: String = Iterator[Iterator[String]](
          if (isEmptyValue) {
            List.empty[String].iterator
          } else {
            for {
              binder <- statement.binders.iterator
              text <- Iterator(
                "\n",
                binder.name,
                ": ",
                binder.tpe.render(TPrintColors.BlackWhite),
                " = "
              ) ++ pprint.PPrinter.BlackWhite
                .tokenize(binder.value, width = 100)
                .map(_.plainText)
            } yield text
          },
          statement.out.linesIterator.flatMap { line =>
            Iterator("\n// ", line)
          }
        ).flatten.mkString
        DecorationOptions(
          range,
          new l.MarkedString("scala", hoverMessage),
          ThemableDecorationInstanceRenderOptions(
            after = ThemableDecorationAttachmentRenderOptions(
              contentText,
              color = "green",
              fontStyle = "italic"
            )
          )
        )
      }
      languageClient.publishDiagnostics(
        new PublishDiagnosticsParams(
          path.toURI.toString(),
          reporter.diagnostics.toSeq.asJava
        )
      )
      decorations
        .filterNot(_.renderOptions.after.contentText == commentHeader)
        .toArray
    }
    decorations.getOrElse(Array.empty)
  }

  private def getContext(path: AbsolutePath): Option[Context] = {
    for {
      target <- buildTargets.inverseSources(path)
      info <- buildTargets.scalaTarget(target)
      scala <- info.info.asScalaBuildTarget
      scalaVersion = scala.getScalaVersion
      isSupported = ScalaVersions.isSupportedScalaVersion(scalaVersion)
      _ = {
        if (!isSupported) {
          scribe.warn(
            s"worksheet: unsupported Scala version '${scalaVersion}', to fix this problem use Scala version '${BuildInfo.scala212}' instead."
          )
        }
      }
      if isSupported
    } yield contexts.getOrElseUpdate(target, newContext(target, info))
  }

  private def newContext(
      target: BuildTargetIdentifier,
      info: ScalaTarget
  ): Context = {
    scribe.info(s"worksheet: new compiler for $target")
    val settings = Settings.default(workspace)
    val classpath = Classpath(info.classpath).syntax
    val options = info.scalac.getOptions().asScala.mkString(" ")
    val compiler = MarkdownCompiler.fromClasspath(classpath, options)
    Context(settings, reporter, compiler)
  }
}
