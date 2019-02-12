package scala.meta.internal.metals

import com.thoughtworks.qdox.model.JavaAnnotatedElement
import com.thoughtworks.qdox.model.JavaConstructor
import com.thoughtworks.qdox.model.JavaGenericDeclaration
import com.thoughtworks.qdox.model.JavaMethod
import com.thoughtworks.qdox.model.JavaParameter
import com.thoughtworks.qdox.model.JavaTypeVariable
import java.util
import scala.collection.JavaConverters._
import scala.meta.internal.semanticdb.Scala.Descriptor
import scala.meta.internal.semanticdb.Scala.Symbols
import scala.meta.pc.SymbolDocumentation

class MetalsSymbolDocumentation(
    val symbol: String,
    val name: String,
    val docstring: String,
    val defaultValue: String = "",
    val typeParameters: util.List[SymbolDocumentation] = Nil.asJava,
    val parameters: util.List[SymbolDocumentation] = Nil.asJava
) extends SymbolDocumentation {
  override def toString: String = {
    val tparamsFormat =
      if (typeParameters.isEmpty) ""
      else typeParameters.asScala.mkString("[", ", ", "]")
    val paramsFormat =
      if (parameters.isEmpty) ""
      else parameters.asScala.mkString(", ")
    s"$name$tparamsFormat($paramsFormat)"
  }
}

object MetalsSymbolDocumentation {
  def fromMethod(symbol: String, method: JavaMethod): SymbolDocumentation = {
    new MetalsSymbolDocumentation(
      symbol,
      method.getName,
      method.getComment,
      "",
      typeParameters(symbol, method, method.getTypeParameters),
      parameters(symbol, method, method.getParameters)
    )
  }
  def fromConstructor(
      symbol: String,
      method: JavaConstructor
  ): SymbolDocumentation = {
    new MetalsSymbolDocumentation(
      symbol,
      method.getName,
      method.getComment,
      "",
      typeParameters(symbol, method, method.getTypeParameters),
      parameters(symbol, method, method.getParameters)
    )
  }
  def param(
      symbol: String,
      name: String,
      docstring: String
  ): SymbolDocumentation =
    new MetalsSymbolDocumentation(
      symbol,
      name,
      if (docstring == null) "" else docstring,
      ""
    )
  def typeParameters[D <: JavaGenericDeclaration](
      owner: String,
      method: JavaAnnotatedElement,
      tparams: util.List[JavaTypeVariable[D]]
  ): util.List[SymbolDocumentation] = {
    tparams.asScala.map { tparam =>
      val tparamName = s"<${tparam.getName}>"
      val docstring = method.getTagsByName("param").asScala.collectFirst {
        case tag if tag.getValue.startsWith(tparamName) =>
          tag.getValue
      }
      this.param(
        Symbols.Global(owner, Descriptor.TypeParameter(tparam.getName)),
        tparam.getName,
        docstring.getOrElse("")
      )
    }.asJava
  }
  def parameters(
      owner: String,
      method: JavaAnnotatedElement,
      params: util.List[JavaParameter]
  ): util.List[SymbolDocumentation] = {
    params.asScala.map { param =>
      val docstring = method.getTagsByName("param").asScala.collectFirst {
        case tag if tag.getValue.startsWith(param.getName) =>
          tag.getValue
      }
      this.param(
        Symbols.Global(owner, Descriptor.Parameter(param.getName)),
        param.getName,
        docstring.getOrElse("")
      )
    }.asJava
  }

}
