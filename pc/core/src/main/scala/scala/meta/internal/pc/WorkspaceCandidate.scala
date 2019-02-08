package scala.meta.internal.pc

import scala.meta.internal.metals.Fuzzy
import scala.meta.internal.semanticdb.Scala._

sealed abstract class WorkspaceCandidate {
  final def nameLength(query: String): Int = Fuzzy.nameLength(nameString)
  final def innerClassDepth: Int =
    WorkspaceCandidate.characterCount(nameString, termCharacter)
  def names: Seq[Descriptor]
  def termCharacter: Char
  def nameString: String
  def packageString: String
}
object WorkspaceCandidate {
  final case class Classfile(pkg: String, filename: String)
      extends WorkspaceCandidate {
    override def names: Seq[Descriptor] =
      filename
        .stripSuffix(".class")
        .split('$')
        .iterator
        .filterNot(_.isEmpty)
        .map(s => Descriptor.TypeParameter(s))
        .toList
    def nameString: String = filename
    override def packageString: String = pkg
    override def termCharacter: Char = '$'
  }
  final case class Workspace(symbol: String) extends WorkspaceCandidate {
    def nameString: String = symbol
    override def names: Seq[Descriptor] = {
      val buf = List.newBuilder[Descriptor]
      def loop(s: String): Unit = {
        if (s.isNone && s.isPackage) ()
        else {
          val (desc, owner) = DescriptorParser(s)
          loop(owner)
          buf += desc
        }
      }
      loop(symbol)
      buf.result()
    }
    override def packageString: String = symbol
    override def termCharacter: Char = '.'
  }
  class Comparator(query: String)
      extends java.util.Comparator[WorkspaceCandidate] {
    override def compare(
        o1: WorkspaceCandidate,
        o2: WorkspaceCandidate
    ): Int = {
      val byNameLength =
        Integer.compare(o1.nameLength(query), o2.nameLength(query))
      if (byNameLength != 0) byNameLength
      else {
        val byInnerclassDepth =
          Integer.compare(o1.innerClassDepth, o2.innerClassDepth)
        if (byInnerclassDepth != 0) byInnerclassDepth
        else {
          val byFirstQueryCharacter = Integer.compare(
            o1.nameString.indexOf(query.head),
            o2.nameString.indexOf(query.head)
          )
          if (byFirstQueryCharacter != 0) {
            byFirstQueryCharacter
          } else {
            val byPackageDepth = Integer.compare(
              characterCount(o1.packageString, '/'),
              characterCount(o2.packageString, '/')
            )
            if (byPackageDepth != 0) byPackageDepth
            else o1.nameString.compareTo(o2.nameString)
          }
        }
      }
    }
  }
  private def characterCount(string: CharSequence, ch: Char): Int = {
    var i = 0
    var count = 0
    while (i < string.length) {
      if (string.charAt(i) == ch) {
        count += 1
      }
      i += 1
    }
    count
  }
}
