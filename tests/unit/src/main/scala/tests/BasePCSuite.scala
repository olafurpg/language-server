package tests

import java.net.URLClassLoader
import java.nio.file.Paths
import scala.meta.internal.metals.JdkSources
import scala.meta.internal.pc.ScalaPC

abstract class BasePCSuite extends BaseSuite {
  val myclasspath = this.getClass.getClassLoader
    .asInstanceOf[URLClassLoader]
    .getURLs
    .map(url => Paths.get(url.toURI))
  val indexer = new SimpleJavaSymbolIndexer(List(JdkSources().get))
  val pc = new ScalaPC(myclasspath, Nil, indexer)
  override def afterAll(): Unit = {
    pc.shutdown()
  }
}
