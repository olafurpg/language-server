package scala.meta.internal.pc

import com.google.gson.JsonObject
import org.eclipse.lsp4j.CompletionItem

class CompletionItemResolver(
    val compiler: PresentationCompiler
) {
  def resolve(item: CompletionItem, msym: String): CompletionItem = {
    val gsym = compiler.inverseSemanticdbSymbol(msym)
    pprint.log(msym)
    pprint.log(gsym.fullName)
    if (gsym != compiler.NoSymbol) {
      val printer = new compiler.SignaturePrinter(
        gsym,
        new compiler.ShortenedNames(),
        gsym.info,
        includeDocs = true
      )
      item.setDetail(printer.defaultMethodSignature)
      item.setDocumentation(printer.methodDocstring)
      item
    } else {
      item
    }
  }
}
