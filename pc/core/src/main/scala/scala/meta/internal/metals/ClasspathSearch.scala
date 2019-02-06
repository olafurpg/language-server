package scala.meta.internal.metals

import java.nio.file.Path
import java.util
import java.util.Comparator
import org.eclipse.lsp4j.jsonrpc.CancelChecker
import scala.collection.concurrent.TrieMap
import scala.meta.io.AbsolutePath

class ClasspathSearch(
    map: collection.Map[String, CompressedPackageIndex],
    packagePriority: String => Int
) {
  private val byReferenceThenAlphabeticalComparator = new Comparator[String] {
    override def compare(a: String, b: String): Int = {
      val byReference = -Integer.compare(packagePriority(a), packagePriority(b))
      if (byReference != 0) byReference
      else a.compare(b)
    }
  }

  private def packagesSortedByReferences(): Array[String] = {
    val packages = map.keys.toArray
    util.Arrays.sort(packages, byReferenceThenAlphabeticalComparator)
    packages
  }
  def search(query: String): Iterator[Classfile] = {
    search(WorkspaceSymbolQuery.exact(query), new CancelChecker {
      override def checkCanceled(): Unit = ()
    })
  }
  def search(
      query: WorkspaceSymbolQuery,
      token: CancelChecker
  ): Iterator[Classfile] = {
    val packages = packagesSortedByReferences()
    for {
      pkg <- packages.iterator
      compressed = map(pkg)
      _ = token.checkCanceled()
      if query.matches(compressed.bloom)
      member <- compressed.members
      if member.endsWith(".class")
      symbol = new ConcatSequence(pkg, member)
      isMatch = query.matches(symbol)
      _ = {
        if (member.contains("Path.class")) {
          pprint.log(symbol)
          pprint.log(isMatch)
        }
      }
      if isMatch
    } yield Classfile(pkg, member)
  }

}

object ClasspathSearch {
  def empty: ClasspathSearch = new ClasspathSearch(Map.empty, _ => 0)
  def fromPackages(
      packages: PackageIndex,
      packagePriority: String => Int
  ): ClasspathSearch = {
    val map = TrieMap.empty[String, CompressedPackageIndex]
    map ++= CompressedPackageIndex.fromPackages(packages)
    new ClasspathSearch(map, packagePriority)
  }
  def fromClasspath(
      classpath: Seq[Path],
      packagePriority: String => Int
  ): ClasspathSearch = {
    val packages = new PackageIndex
    packages.visitBootClasspath()
    classpath.foreach { path =>
      packages.visit(AbsolutePath(path))
    }
    fromPackages(packages, packagePriority)
  }
}
