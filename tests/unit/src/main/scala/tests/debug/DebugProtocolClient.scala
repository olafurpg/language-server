package tests.debug
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory._
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import scala.concurrent.Future
import scala.concurrent.Promise

protected[debug] final class DebugProtocolClient extends IDebugProtocolClient {
  val output = new OutputChannels
  private val terminationHandle = Promise[Unit]()

  def sessionTerminated(): Future[Unit] = terminationHandle.future

  override def output(args: OutputEventArguments): Unit =
    output.append(args)

  override def terminated(args: TerminatedEventArguments): Unit = {
    terminationHandle.success(())
  }

  class OutputChannels {
    private val outBuffer = new StringBuilder
    private val errBuffer = new StringBuilder

    def stdout: String = outBuffer.toString()
    def stderr: String = errBuffer.toString()

    private[DebugProtocolClient] def append(args: OutputEventArguments): Unit =
      args.getCategory match {
        case STDOUT =>
          outBuffer.append(args.getOutput)
        case STDERR =>
          errBuffer.append(args.getOutput)
        case category =>
          throw new IllegalStateException(s"Unsupported output: $category")
      }
  }
}
