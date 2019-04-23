package scala.meta.internal.metals

import scala.collection.mutable
import scala.collection.concurrent.TrieMap
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.semanticdb.ClassSignature
import scala.meta.internal.semanticdb.TypeRef
import java.nio.file.Path
import scala.meta.io.AbsolutePath

case class Subclass(semanticdbFile: AbsolutePath, symbol: String)
case class SuperclassValue(
    semanticdbFile: Path,
    parents: collection.Set[String]
)
class Superclasses {
  val index = TrieMap.empty[String, SuperclassValue]
  val isUninterestingSuperclass = Set(
    "scala/Serializable#",
    "scala/Product#",
    "scala/AnyRef#",
    "java/lang/Object#"
  )
  def allKnownSubclasses(
      symbol: String,
      isVisited: mutable.Set[String] = mutable.Set.empty[String]
  ): List[Subclass] = {
    val result = mutable.ListBuffer.empty[Subclass]
    def loop(s: String): Unit = {
      if (!isVisited(s)) {
        isVisited += s
        for {
          (child, value) <- index.iterator
          if value.parents.contains(s) || child == symbol
        } {
          result += Subclass(AbsolutePath(value.semanticdbFile), child)
          loop(child)
        }
      }
    }
    loop(symbol)
    result.toList
  }

  def visitSymbol(semanticdbFile: Path, info: SymbolInformation): Unit =
    info.signature match {
      case c: ClassSignature =>
        var buf: collection.Set[String] = null
        c.parents.foreach { parent =>
          parent match {
            case ref: TypeRef =>
              if (!isUninterestingSuperclass(ref.symbol)) {
                if (buf == null) {
                  buf = new mutable.LinkedHashSet[String]()
                }
                buf += ref.symbol
              }
            case _ =>
          }
        }
        if (!buf.isEmpty) {
          index(info.symbol) = SuperclassValue(semanticdbFile, buf)
        }
      case _ =>
    }
}
