package scala.meta.internal.pc

import scala.tools.nsc.reporters.StoreReporter
import scala.util.control.NonFatal

class CompilerAccess(newCompiler: () => PresentationCompiler) {
  def isEmpty: Boolean = _compiler == null
  def isDefined: Boolean = !isEmpty
  def reporter: StoreReporter =
    if (isEmpty) new StoreReporter()
    else _compiler.reporter.asInstanceOf[StoreReporter]
  def shutdown(): Unit = {
    if (_compiler != null) {
      _compiler.askShutdown()
      _compiler = null
    }
  }
  def withCompiler[T](default: T)(thunk: PresentationCompiler => T): T = {
    lock.synchronized {
      try thunk(loadCompiler())
      catch {
        case NonFatal(e) =>
          CompilerThrowable.trimStackTrace(e)
          scribe.error(e.getMessage, e)
          shutdown()
          default
      }
    }
  }
  private var _compiler: PresentationCompiler = _
  private val lock = new Object
  private def loadCompiler(): PresentationCompiler = {
    if (_compiler == null) {
      _compiler = newCompiler()
//        ScalaPC.newCompiler(
//        classpath,
//        options,
//        indexer,
//        search
//      )
    }
    _compiler.reporter.reset()
    _compiler
  }
}
