package tests

import scala.collection.JavaConverters._

abstract class BaseSignatureHelpSuite extends BasePCSuite {
  def checkDoc(name: String, code: String, expected: String): Unit = {
    check(name, code, expected, includeDocs = true)
  }
  def check(
      name: String,
      original: String,
      expected: String,
      includeDocs: Boolean = false
  ): Unit = {
    test(name) {
      val (code, offset) = params(original)
      val result = pc.signatureHelp("A.scala", code, offset)
      val out = new StringBuilder()
      if (result != null) {
        result.getSignatures.asScala.zipWithIndex.foreach {
          case (signature, i) =>
            if (includeDocs) {
              val sdoc = doc(signature.getDocumentation)
              if (sdoc.nonEmpty) {
                out.append(sdoc).append("\n")
              }
            }
            out
              .append(signature.getLabel)
              .append("\n")
            if (result.getActiveSignature == i && result.getActiveParameter != null) {
              val param = signature.getParameters.get(result.getActiveParameter)
              val column = signature.getLabel.indexOf(param.getLabel)
              if (column < 0) {
                fail(s"""invalid parameter label
                        |  param.label    : ${param.getLabel}
                        |  signature.label: ${signature.getLabel}
                        |""".stripMargin)
              }
              val documentation = doc(param.getDocumentation)
              val typeSignature =
                if (documentation.startsWith("```scala")) {
                  documentation
                    .stripPrefix("```scala\n")
                    .lines
                    .take(1)
                    .mkString(" ", "", "")
                } else {
                  ""
                }
              val indent = " " * column
              out
                .append(indent)
                .append("^" * param.getLabel.length)
                .append(typeSignature)
                .append("\n")
              signature.getParameters.asScala.foreach { param =>
                val pdoc = doc(param.getDocumentation).trim
                  .stripPrefix("```scala\n")
                  .stripSuffix("\n```")
                if (includeDocs && pdoc.nonEmpty) {
                  out
                    .append("  @param ")
                    .append(param.getLabel.replaceFirst(":.*", ""))
                    .append(" ")
                    .append(pdoc)
                    .append("\n")
                }
              }
            }
        }
      }
      assertNoDiff(out.toString(), expected)
    }
  }
}