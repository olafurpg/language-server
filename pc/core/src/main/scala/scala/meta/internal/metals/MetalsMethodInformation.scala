package scala.meta.internal.metals

import com.thoughtworks.qdox.model.JavaAnnotatedElement
import com.thoughtworks.qdox.model.JavaConstructor
import com.thoughtworks.qdox.model.JavaGenericDeclaration
import com.thoughtworks.qdox.model.JavaMethod
import com.thoughtworks.qdox.model.JavaParameter
import com.thoughtworks.qdox.model.JavaTypeVariable
import java.util
import scala.collection.JavaConverters._
import scala.meta.pc.MethodInformation
import scala.meta.pc.ParameterInformation

class MetalsMethodInformation(
    val symbol: String,
    val name: String,
    val docstring: String,
    val typeParameters: util.List[ParameterInformation],
    val parameters: util.List[ParameterInformation]
) extends MethodInformation {
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

object MetalsMethodInformation {
  def fromMethod(symbol: String, method: JavaMethod): MethodInformation = {
    new MetalsMethodInformation(
      symbol,
      method.getName,
      method.getComment,
      typeParameters(method, method.getTypeParameters),
      parameters(method, method.getParameters)
    )
  }
  def fromConstructor(
      symbol: String,
      method: JavaConstructor
  ): MethodInformation = {
    new MetalsMethodInformation(
      symbol,
      method.getName,
      method.getComment,
      typeParameters(method, method.getTypeParameters),
      parameters(method, method.getParameters)
    )
  }
  def param(name: String, docstring: String): ParameterInformation =
    new MetalsParameterInformation(
      name,
      if (docstring == null) "" else docstring,
      ""
    )
  def typeParameters[D <: JavaGenericDeclaration](
      method: JavaAnnotatedElement,
      tparams: util.List[JavaTypeVariable[D]]
  ): util.List[ParameterInformation] = {
    tparams.asScala.map { tparam =>
      val tparamName = s"<${tparam.getName}>"
      val docstring = method.getTagsByName("param").asScala.collectFirst {
        case tag if tag.getValue.startsWith(tparamName) =>
          tag.getValue
      }
      this.param(tparam.getName, docstring.getOrElse(""))
    }.asJava
  }
  def parameters(
      method: JavaAnnotatedElement,
      params: util.List[JavaParameter]
  ): util.List[ParameterInformation] = {
    params.asScala.map { param =>
      val docstring = method.getTagsByName("param").asScala.collectFirst {
        case tag if tag.getValue.startsWith(param.getName) =>
          tag.getValue
      }
      this.param(param.getName, docstring.getOrElse(""))
    }.asJava
  }

}

class MetalsParameterInformation(
    val name: String,
    val docstring: String,
    val defaultValue: String
) extends ParameterInformation {
  override def toString: String = name
}
