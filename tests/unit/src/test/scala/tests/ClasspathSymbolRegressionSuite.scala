package tests

import java.nio.file.Files
import scala.meta.internal.metals.RecursivelyDelete
import scala.meta.io.AbsolutePath

object ClasspathSymbolRegressionSuite extends BaseWorkspaceSymbolSuite {
  var tmp = AbsolutePath(Files.createTempDirectory("metals"))
  override def libraries: List[Library] = Libraries.suite
  def workspace: AbsolutePath = tmp
  override def afterAll(): Unit = {
    RecursivelyDelete(tmp)
  }

  check(
    "Map.Entry",
    """|java.util.Map#Entry Interface
       |java.util.TreeMap#AscendingSubMap#AscendingEntrySetView Class
       |java.util.TreeMap#DescendingSubMap#DescendingEntrySetView Class
       |java.util.TreeMap#Entry Class
       |java.util.TreeMap#EntryIterator Class
       |java.util.TreeMap#EntrySet Class
       |java.util.TreeMap#EntrySpliterator Class
       |java.util.TreeMap#NavigableSubMap#DescendingSubMapEntryIterator Class
       |java.util.TreeMap#NavigableSubMap#EntrySetView Class
       |java.util.TreeMap#NavigableSubMap#SubMapEntryIterator Class
       |java.util.TreeMap#PrivateEntryIterator Class
       |""".stripMargin
  )

}
