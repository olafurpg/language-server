package tests

import scala.meta.internal.metals.SbtChecksums
import scala.meta.internal.metals.SbtChecksum.Status._

object SbtChecksumsSuite extends BaseTablesSuite {
  def sbtChecksums: SbtChecksums = tables.sbtChecksums
  test("basic") {
    assertEquals(sbtChecksums.setStatus("a", Requested), 1)
    assertEquals(sbtChecksums.getStatus("a").get, Requested)
    assertEquals(sbtChecksums.setStatus("a", Installed), 1)
    assertEquals(sbtChecksums.getStatus("a").get, Installed)
  }
}
