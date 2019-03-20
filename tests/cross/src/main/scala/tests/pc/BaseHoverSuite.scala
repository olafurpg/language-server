package tests.pc

import java.lang.StringBuilder
import org.eclipse.lsp4j.Hover
import scala.collection.JavaConverters._
import scala.meta.inputs.Input
import scala.meta.internal.metals.CompilerOffsetParams
import scala.meta.internal.mtags.MtagsEnrichments._
import tests.BasePCSuite

abstract class BaseHoverSuite extends BasePCSuite {
  def check(
      name: String,
      original: String,
      expected: String,
      includeRange: Boolean = false
  ): Unit = {
    test(name) {
      val filename = "Hover.scala"
      val pkg = scala.meta.Term.Name(name).syntax
      val noRange = original
        .replaceAllLiterally("<<", "")
        .replaceAllLiterally(">>", "")
      val packagePrefix = s"package $pkg\n"
      val codeOriginal = packagePrefix + noRange
      val (code, offset) = params(codeOriginal, filename)
      val hover = pc.hover(
        CompilerOffsetParams(filename, code, offset)
      )
      val obtained: String = hover.asScala match {
        case Some(value) =>
          val types = value.getContents.asScala match {
            case Right(value) =>
              value.getValue
            case Left(values) =>
              values.asScala
                .map { e =>
                  e.asScala match {
                    case Left(value) =>
                      value
                    case Right(marked) =>
                      codeFence(marked.getValue, marked.getLanguage)
                  }
                }
                .mkString("\n")
          }
          val range = Option(value.getRange) match {
            case Some(value) if includeRange =>
              codeFence(
                value.toMeta(Input.String(code)).text,
                "range"
              )
            case _ => ""
          }
          List(types, range).filterNot(_.isEmpty).mkString("\n")
        case None =>
          ""
      }
      assertNoDiff(obtained, expected)
      for {
        h <- hover.asScala
        range <- Option(h.getRange)
      } {
        val base = codeOriginal.replaceAllLiterally("@@", "")
        val input = Input.String(base)
        val pos = range.toMeta(input)
        val withRange = new StringBuilder()
          .append(base, 0, pos.start)
          .append("<<")
          .append(base, pos.start, pos.end)
          .append(">>")
          .append(base, pos.end, base.length)
          .toString
        assertNoDiff(
          withRange,
          packagePrefix + original.replaceAllLiterally("@@", ""),
          "Invalid range"
        )
      }
    }
  }

  private def toScala(hover: Hover) = {
    hover.getContents
  }
  private def codeFence(code: String, language: String): String = {
    val trimmed = code.trim
    if (trimmed.isEmpty) ""
    else {
      new StringBuilder()
        .append("```")
        .append(language)
        .append("\n")
        .append(trimmed)
        .append("\n")
        .append("```")
        .toString()
    }
  }
}
