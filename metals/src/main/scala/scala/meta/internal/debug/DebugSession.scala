package scala.meta.internal.debug

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Files.createTempDirectory
import java.nio.file.Paths
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.debug.protocol.LaunchParameters
import scala.meta.internal.debug.protocol.OutputEventArguments._
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

final class DebugSession(
    process: Process,
    val exitCode: Future[Int]
) {
  def whenTerminated[A](f: Int => A)(implicit ec: ExecutionContext): Future[A] =
    exitCode.map(f)
}

final class DebugSessionFactory(client: IDebugProtocolClient) {
  def create(
      params: LaunchParameters
  )(implicit ec: ExecutionContext): DebugSession = {
    val command = createCommand(params)
    val workingDir = createWorkingDir(params)
    val listener = new Listener()

    val process = Process(command, workingDir).run(listener)

    val exitCode = Future(process.exitValue())
    new DebugSession(process, exitCode)
  }

  private def createCommand(params: LaunchParameters): Array[String] = {
    val java = Paths
      .get(System.getProperty("java.home"))
      .resolve("bin")
      .resolve("java")
      .toString

    val classpath = params.classpath.mkString(File.pathSeparator)
    Array(java, "-cp", classpath, params.mainClass)
  }

  private def createWorkingDir(params: LaunchParameters): File = {
    Option(params.cwd)
      .map(URI.create)
      .map(Paths.get)
      .filter(Files.isDirectory(_))
      .map(_.toFile)
      .getOrElse {
        // prevent using the working dir of the current process
        val workingDir = createTempDirectory("metals-debug-session-").toFile
        workingDir.deleteOnExit()
        workingDir
      }
  }

  private class Listener extends ProcessLogger {
    override def out(s: => String): Unit = client.output(stdout(s))
    override def err(s: => String): Unit = client.output(stderr(s))
    override def buffer[T](f: => T): T = f
  }
}
