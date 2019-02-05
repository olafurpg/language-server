package tests

import org.eclipse.lsp4j.CompletionItem
import scala.collection.JavaConverters._

abstract class BaseCompletionSuite extends BasePCSuite {
  def check(
      name: String,
      original: String,
      expected: String,
      includeDocs: Boolean = false
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
        out
          .append(item.getLabel)
          .append(item.getDetail)
          .append("\n")
      }
      pprint.log(items.length)
//      assertNoDiff(out.toString(), expected)
    }
  }
}
