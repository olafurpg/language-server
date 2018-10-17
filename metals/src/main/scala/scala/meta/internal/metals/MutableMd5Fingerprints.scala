package scala.meta.internal.metals

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.concurrent.TrieMap
import scala.meta.internal.mtags.Md5Fingerprints
import scala.meta.io.AbsolutePath
import scala.collection.JavaConverters._
import scala.meta.internal.mtags.MD5

final class MutableMd5Fingerprints extends Md5Fingerprints {
  private case class Fingerprint(text: String, md5: String)
  private val fingerprints =
    TrieMap.empty[AbsolutePath, ConcurrentLinkedQueue[Fingerprint]]
  def add(path: AbsolutePath, text: String): Unit = {
    val md5 = MD5.compute(text)
    val value = fingerprints.getOrElseUpdate(path, new ConcurrentLinkedQueue())
    value.add(Fingerprint(text, md5))
  }
  override def lookupText(path: AbsolutePath, md5: String): Option[String] = {
    for {
      prints <- fingerprints.get(path)
      fingerprint <- prints.asScala.find(_.md5 == md5)
    } yield {
      // remove non-active md5 fingerprints.
      prints.clear()
      prints.add(fingerprint)
      fingerprint.text
    }
  }
  override def toString: String = s"Md5FingerprintProvider($fingerprints)"
}
