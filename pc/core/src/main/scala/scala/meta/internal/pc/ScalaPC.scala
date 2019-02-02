package scala.meta.internal.pc

import java.io.File
import java.nio.file.Path
import java.util
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.SignatureHelp
import scala.meta.pc.CompletionItems
import scala.meta.pc.PC
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.Settings
import scala.collection.JavaConverters._
import scala.meta.pc.SymbolIndexer
import scala.tools.nsc.interactive.Global
import scala.tools.nsc.interactive.Response
import scala.tools.nsc.reporters.StoreReporter

class ScalaPC(
    classpath: Seq[Path],
    options: Seq[String],
    indexer: SymbolIndexer,
    private var _global: ScalaCompiler = null
) extends PC {
  override def withIndexer(indexer: SymbolIndexer): PC =
    new ScalaPC(classpath, options, indexer, global)
  def this() = this(Nil, Nil, new EmptySymbolIndexer)
  def global: ScalaCompiler = {
    if (_global == null) {
      _global = ScalaPC.newCompiler(
        classpath.mkString(File.pathSeparator),
        options,
        indexer
      )
    }
    _global
  }

  override def complete(
      filename: String,
      text: String,
      offset: Int
  ): CompletionItems = {
    new CompletionProvider(global).completions(filename, text, offset)
  }

  override def hover(filename: String, text: String, offset: Int): Hover = {
    new HoverProvider(global).hover(filename, text, offset).orNull
  }
  override def newInstance(
      classpath: util.List[Path],
      options: util.List[String]
  ): PC = {
    new ScalaPC(classpath.asScala, options.asScala, indexer)
  }

  override def shutdown(): Unit = {
    if (_global != null) {
      _global.askShutdown()
    }
  }

  override def signatureHelp(
      filename: String,
      text: String,
      offset: Int
  ): SignatureHelp = {
    new SignatureHelpProvider(global, indexer)
      .signatureHelp(filename, text, offset)
  }

  override def symbol(filename: String, text: String, offset: Int): String = {
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
      classpath: String,
      scalacOptions: Seq[String],
      indexer: SymbolIndexer
  ): ScalaCompiler = {
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
    new ScalaCompiler(settings, new StoreReporter, indexer)
  }

  def ask[A](f: Response[A] => Unit): Response[A] = {
    val r = new Response[A]
    f(r)
    r
  }

}
