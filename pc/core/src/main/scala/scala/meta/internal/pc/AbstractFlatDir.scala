package scala.meta.internal.pc

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import scala.collection.mutable.ArrayBuffer
import scala.reflect.io.NoAbstractFile
import scala.tools.nsc.io.AbstractFile

class AbstractFlatDir(
    val path: String,
    val children: ArrayBuffer[AbstractFile] = ArrayBuffer.empty
) extends AbstractFile {
  private lazy val files: Map[String, AbstractFile] =
    children.map(c => c.name -> c)(collection.breakOut)
  override val name: String = path.split('/').last
  override def absolute: AbstractFile = this
  override def container: AbstractFile = NoAbstractFile
  override def file: File = null
  override def create(): Unit = unsupported()
  override def delete(): Unit = unsupported()
  override def isDirectory: Boolean = true
  val lastModified: Long = System.currentTimeMillis
  override def input: InputStream = unsupported()
  override def output: OutputStream = unsupported()
  override def iterator: Iterator[AbstractFile] = {
    children.iterator
  }
  override def lookupNameUnchecked(
      name: String,
      directory: Boolean
  ): AbstractFile = {
    unsupported()
  }

  override def lookupName(name: String, directory: Boolean): AbstractFile = {
    files.get(name).filter(_.isDirectory == directory).orNull
  }

  override def toString(): String =
    s"AFD($path, ${children.map(_.toString).mkString(",")})\n\n"
}
