package scala.meta.internal.metals

import java.util.Arrays
import java.nio.file.Path
import java.util.Comparator
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
    Arrays.sort(packages, byReferenceThenAlphabeticalComparator)
    packages
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
    classpath.foreach { path =>
      packages.visit(AbsolutePath(path))
    }
    fromPackages(packages, packagePriority)
  }
}
