package tests

import org.eclipse.lsp4j.CompletionItem
import scala.collection.JavaConverters._
import scala.util.Properties

abstract class BaseCompletionSuite extends BasePCSuite {
  def checkLength(
      name: String,
      original: String,
      expected: Int,
      compat: Map[String, Int] = Map.empty
  ): Unit = {
    test(name) {
      val (code, offset) = params(original)
      val result = pc.complete("A.scala", code, offset)
      assertEquals(
        result.getItems.size(),
        getExpected(expected, compat)
      )
    }
  }

  def check(
      name: String,
      original: String,
      expected: String,
      includeDocs: Boolean = false,
      compat: Map[String, String] = Map.empty
  ): Unit = {
    test(name) {
      val (code, offset) = params(original)
      val result = pc.complete("A.scala", code, offset)
      val out = new StringBuilder()
      val items = result.getItems.asScala.sorted(new Ordering[CompletionItem] {
        override def compare(o1: CompletionItem, o2: CompletionItem): Int = {
          if (o1.getLabel == o2.getLabel) o1.getDetail.compareTo(o2.getDetail)
          else o1.getSortText.compareTo(o2.getSortText)
        }
      })
      items.foreach { item =>
        val label =
          if (item.getInsertText == null) item.getLabel else item.getInsertText
        out
          .append(label)
          .append(item.getDetail)
          .append("\n")
      }
      assertNoDiff(out.toString(), getExpected(expected, compat))
    }
  }

  private def scalaVersion: String =
    Properties.versionNumberString
  private def scalaBinary: String =
    scalaVersion.split("\\.").take(2).mkString(".")
  private def getExpected[T](default: T, compat: Map[String, T]): T = {
    compat
      .get(scalaBinary)
      .orElse(compat.get(scalaVersion))
      .getOrElse(default)
  }
}