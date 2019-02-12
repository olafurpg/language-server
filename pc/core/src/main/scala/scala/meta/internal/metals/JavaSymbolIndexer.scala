package scala.meta.internal.metals

import com.thoughtworks.qdox.model.JavaConstructor
import com.thoughtworks.qdox.model.JavaMethod
import scala.meta.inputs.Input
import scala.meta.inputs.Position
import scala.meta.internal.mtags.JavaMtags
import scala.meta.internal.semanticdb.Scala.Descriptor
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolVisitor

class JavaSymbolIndexer(input: Input.VirtualFile) extends SymbolIndexer {
  override def visit(symbol: String, visitor: SymbolVisitor): Unit = {
    val mtags = new JavaMtags(input) {
      override def visitConstructor(
          ctor: JavaConstructor,
          disambiguator: String,
          pos: Position,
          properties: Int
      ): Unit = {
        visitor.visitMethod(
          MetalsSymbolDocumentation.fromConstructor(
            symbol(Descriptor.Method("<init>", disambiguator)),
            ctor
          )
        )
      }
      override def visitMethod(
          method: JavaMethod,
          name: String,
          disambiguator: String,
          pos: Position,
          properties: Int
      ): Unit = {
        visitor.visitMethod(
          MetalsSymbolDocumentation.fromMethod(
            symbol(Descriptor.Method(name, disambiguator)),
            method
          )
        )
      }
    }
    mtags.indexRoot()
  }
}
