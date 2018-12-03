package tests

import java.util.jar.JarFile
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.meta.internal.io._
import scala.reflect.NameTransformer

object FuzzSuite extends BaseSuite {

  test("fuzz") {
    val cp = InputProperties.default()
    val query = "AD"
    val buf = ListBuffer.empty[String]
    cp.classpath.entries.foreach { entry =>
      if (PathIO.extension(entry.toNIO) == "jar") {}
    }
    pprint.log(buf)
  }
}
