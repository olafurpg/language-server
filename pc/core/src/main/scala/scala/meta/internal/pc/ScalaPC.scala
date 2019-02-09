package scala.meta.internal.pc

import java.io.File
import java.nio.file.Path
import java.util
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.SignatureHelp
import scala.meta.pc.CompletionItems
import scala.meta.pc.PC
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.Settings
import scala.collection.JavaConverters._
import scala.meta.pc.CompletionItems.LookupKind
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolSearch
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.interactive.Response
import scala.tools.nsc.reporters.StoreReporter

class ScalaPC(
    classpath: Seq[Path],
    options: Seq[String],
    indexer: SymbolIndexer,
    search: SymbolSearch,
    access: CompilerAccess,
    buildTargetIdentifier: String
) extends PC {
  override def withIndexer(indexer: SymbolIndexer): PC =
    new ScalaPC(
      classpath,
      options,
      indexer,
      search,
      access,
      buildTargetIdentifier
    )
  override def withSearch(search: SymbolSearch): PC =
    new ScalaPC(
      classpath,
      options,
      indexer,
      search,
      access,
      buildTargetIdentifier
    )
  def this() =
    this(
      Nil,
      Nil,
      new EmptySymbolIndexer,
      EmptySymbolSearch,
      new CompilerAccess(
        () => {
          ScalaPC.newCompiler(
            Nil,
            Nil,
            new EmptySymbolIndexer,
            EmptySymbolSearch,
            ""
          )
        }
      )
    )

  override def shutdown(): Unit = {
    access.shutdown()
  }

  override def newInstance(
      buildTargetIdentifier: String,
      classpath: util.List[Path],
      options: util.List[String]
  ): PC = {
    new ScalaPC(
      classpath.asScala,
      options.asScala,
      indexer,
      search,
      new CompilerAccess(
        () => {
          ScalaPC.newCompiler(
            classpath.asScala,
            options.asScala,
            indexer,
            search,
            buildTargetIdentifier
          )
        }
      ),
      buildTargetIdentifier
    )
  }

  override def diagnostics(): util.List[String] = {
    access.reporter
      .asInstanceOf[StoreReporter]
      .infos
      .iterator
      .map(
        info =>
          new StringBuilder()
            .append(info.pos.source.file.path)
            .append(":")
            .append(info.pos.column)
            .append(" ")
            .append(info.msg)
            .append("\n")
            .append(info.pos.lineContent)
            .append("\n")
            .append(info.pos.lineCaret)
            .toString
      )
      .filterNot(_.contains("_CURSOR_"))
      .toList
      .asJava
  }

  def emptyCompletion = new CompletionItems(LookupKind.None, Nil.asJava)
  override def complete(
      filename: String,
      text: String,
      offset: Int
  ): CompletionItems =
    access.withCompiler(emptyCompletion) { global =>
      new CompletionProvider(global).completions(filename, text, offset)
    }

  override def hover(filename: String, text: String, offset: Int): Hover =
    access.withCompiler(new Hover()) { global =>
      new HoverProvider(global).hover(filename, text, offset).orNull
    }

  override def signatureHelp(
      filename: String,
      text: String,
      offset: Int
  ): SignatureHelp = access.withCompiler(new SignatureHelp()) { global =>
    new SignatureHelpProvider(global, indexer)
      .signatureHelp(filename, text, offset)
  }

  override def symbol(filename: String, text: String, offset: Int): String = {
    access.withCompiler("") { global =>
      val unit = ScalaPC.addCompilationUnit(
        global = global,
        code = text,
        filename = filename,
        cursor = None
      )
      val pos = unit.position(offset)
      global.typedTreeAt(pos).symbol.fullName
    }
  }
}
object ScalaPC {

  def addCompilationUnit(
      global: Global,
      code: String,
      filename: String,
      cursor: Option[Int],
      cursorName: String = "_CURSOR_"
  ): global.RichCompilationUnit = {
    val codeWithCursor = cursor match {
      case Some(offset) =>
        code.take(offset) + cursorName + code.drop(offset)
      case _ => code
    }
    val unit = global.newCompilationUnit(codeWithCursor, filename)
    val richUnit = new global.RichCompilationUnit(unit.source)
    global.unitOfFile(richUnit.source.file) = richUnit
    richUnit
  }

  def newCompiler(
      classpaths: Seq[Path],
      scalacOptions: Seq[String],
      indexer: SymbolIndexer,
      search: SymbolSearch,
      buildTargetIdentifier: String
  ): PresentationCompiler = {
    val classpath = classpaths.mkString(File.pathSeparator)
    val options = scalacOptions.iterator.filterNot { o =>
      o.contains("semanticdb") ||
      o.contains("scalajs")
    }.toList
    val vd = new VirtualDirectory("(memory)", None)
    val settings = new Settings
    settings.outputDirs.setSingleOutput(vd)
    settings.classpath.value = classpath
    settings.YpresentationAnyThread.value = true
    if (classpath.isEmpty) {
      settings.usejavacp.value = true
    }
    val (isSuccess, unprocessed) =
      settings.processArguments(options, processAll = true)
    require(isSuccess, unprocessed)
    require(unprocessed.isEmpty, unprocessed)
    new PresentationCompiler(
      settings,
      new StoreReporter,
      indexer,
      search,
      buildTargetIdentifier
    )
  }

  def ask[A](f: Response[A] => Unit): Response[A] = {
    val r = new Response[A]
    f(r)
    r
  }

}
