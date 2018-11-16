package tests
import java.nio.file.Files
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService
import scala.meta.internal.io.PathIO
import scala.meta.internal.metals.BloopProtocol
import scala.meta.internal.metals.Buffers
import scala.meta.internal.metals.MetalsServerConfig
import scala.meta.io.AbsolutePath

/**
 * Full end to end integration tests against a full metals language server.
 */
abstract class BaseSlowSuite extends BaseSuite {
  def protocol: BloopProtocol = BloopProtocol.Auto
  implicit val ex: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
  var server: TestingServer = _
  var client: TestingClient = _
  var workspace: AbsolutePath = _
  override def afterAll(): Unit = {
    if (server != null) {
      server.cancel()
    }
    ex.shutdown()
  }
  override def utestBeforeEach(path: Seq[String]): Unit = {
    if (server != null) {
      server.cancel()
    }
    val name = path.last
    workspace = PathIO.workingDirectory
      .resolve("target")
      .resolve("e2e")
      .resolve(name.replace(' ', '-'))
    Files.createDirectories(workspace.toNIO)
    val buffers = Buffers()
    val config = MetalsServerConfig.default.copy(bloopProtocol = protocol)
    client = new TestingClient(workspace, buffers)
    server =
      new TestingServer(workspace, client, workspace, buffers, config)(ex)
  }

  def cleanDatabase(): Unit = {
    Files.delete(workspace.resolve(".metals").resolve("metals.h2.db").toNIO)
  }
  def cleanWorkspace(): Unit = {
    RecursivelyDelete(workspace.resolve(".metals"))
    RecursivelyDelete(workspace.resolve(".bloop"))
  }
}
