package scala.meta.internal.pc

import org.eclipse.lsp4j.CompletionItem
import scala.collection.JavaConverters._
import scala.util.Try

class CompletionItemResolver(
    val compiler: PresentationCompiler
) {
  def resolve(item: CompletionItem, msym: String): CompletionItem = {
    val gsym = compiler.inverseSemanticdbSymbol(msym)
    if (gsym != compiler.NoSymbol) {
      compiler.methodInfo(gsym) match {
        case Some(info) if item.getDetail != null =>
          if (compiler.isJavaSymbol(gsym)) {
            val newDetail = info
              .parameters()
              .asScala
              .iterator
              .zipWithIndex
              .foldLeft(item.getDetail) {
                case (detail, (param, i)) =>
                  detail.replaceAllLiterally(s"x$$${i + 1}", param.name())
              }
            item.setDetail(newDetail)
          } else {
            val defaults = info
              .parameters()
              .asScala
              .iterator
              .map(_.defaultValue())
              .filterNot(_.isEmpty)
            val matcher = "= \\{\\}".r.pattern.matcher(item.getDetail)
            val out = new StringBuffer()
            while (matcher.find()) {
              if (defaults.hasNext) {
                matcher.appendReplacement(out, s"= ${defaults.next()}")
              }
            }
            matcher.appendTail(out)
            item.setDetail(out.toString)
          }
          val docstring =
            if (info.docstring().isEmpty) {
              compiler.methodInfo(gsym.companion).fold("")(_.docstring())
            } else {
              info.docstring()
            }
          item.setDocumentation(docstring)
        case _ =>
      }
      item
    } else {
      item
    }
  }
}
