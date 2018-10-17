package scala.meta.internal.metals

import scala.collection.concurrent.TrieMap
import scala.meta.io.AbsolutePath

case class Buffers(map: TrieMap[AbsolutePath, String] = TrieMap.empty) {
  def open: Iterable[AbsolutePath] = map.keys
  def put(key: AbsolutePath, value: String): Unit = map.put(key, value)
  def get(key: AbsolutePath): Option[String] = map.get(key)
  def remove(key: AbsolutePath): Unit = map.remove(key)
}
