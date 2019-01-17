package tests

import scala.meta.internal.metals.WorkspaceSymbolProvider
import scala.meta.io.AbsolutePath
import tests.MetalsTestEnrichments._

abstract class BaseWorkspaceSymbolSuite extends BaseSuite {
  def workspace: AbsolutePath
  def libraries: List[Library] = Nil
  lazy val symbols: WorkspaceSymbolProvider = {
    val p = TestingWorkspaceSymbolProvider(workspace)
    p.indexWorkspace()
    p.indexLibraries(libraries)
    p.onBuildTargetsUpdate()
    p
  }
  def check(query: String, expected: String): Unit = {
    test(query) {
      val result = symbols.search(query)
      pprint.log(result.length)
      val obtained =
        if (result.length > 100) s"${result.length} results"
        else {
          result
            .map { i =>
              s"${i.getContainerName}${i.getName} ${i.getKind}"
            }
            .sorted
            .mkString("\n")
        }
      assertNoDiff(obtained, expected)
    }
  }
}
