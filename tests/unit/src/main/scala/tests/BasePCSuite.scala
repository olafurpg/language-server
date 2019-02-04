package tests

import com.geirsson.coursiersmall.CoursierSmall
import com.geirsson.coursiersmall.Dependency
import com.geirsson.coursiersmall.Settings
import java.net.URLClassLoader
import java.nio.file.Paths
import scala.meta.internal.metals.JdkSources
import scala.meta.internal.metals.MetalsSymbolIndexer
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.pc.ScalaPC
import scala.meta.internal.metals.{BuildInfo => V}
import scala.meta.io.AbsolutePath

abstract class BasePCSuite extends BaseSuite {
  val myclasspath = this.getClass.getClassLoader
    .asInstanceOf[URLClassLoader]
    .getURLs
    .map(url => Paths.get(url.toURI))
  val index = OnDemandSymbolIndex()
  val indexer = new MetalsSymbolIndexer(index)
  val pc = new ScalaPC(myclasspath, Nil, indexer)

  override def beforeAll(): Unit = {
    index.addSourceJar(JdkSources().get)
    val sources = CoursierSmall.fetch(
      new Settings()
        .withClassifiers(List("sources"))
        .withDependencies(
          List(new Dependency("org.scala-lang", "scala-library", V.scala212))
        )
    )
    index.addSourceJar(JdkSources().get)
    sources.foreach { jar =>
      index.addSourceJar(AbsolutePath(jar))
    }
  }
  override def afterAll(): Unit = {
    pc.shutdown()
  }

  def params(code: String): (String, Int) = {
    val code2 = code.replaceAllLiterally("@@", "")
    val offset = code.indexOf("@@")
    if (offset < 0) {
      fail("missing @@")
    }
    (code2, offset)
  }
}
