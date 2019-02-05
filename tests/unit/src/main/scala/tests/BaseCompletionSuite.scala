package tests

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
      result.getItems.asScala.foreach { item =>
        out
          .append(item.getLabel)
          .append(item.getDetail)
          .append("\n")
      }
      assertNoDiff(out.toString(), expected)
    }
  }
}
