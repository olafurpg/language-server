package scala.meta.internal.pc

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import scala.reflect.io.NoAbstractFile
import scala.tools.nsc.io.AbstractFile

class AbstractFlatFile(
    flatFile: FlatFile,
    flatJar: FlatJar,
    ffs: FlatFileSystem
) extends AbstractFile {
  override val path = flatFile.path
  override val name: String = path.split('/').last
  override def absolute: AbstractFile = this
  override def container: AbstractFile = NoAbstractFile
  override def file: File = null
  override def create(): Unit = unsupported()
  override def delete(): Unit = unsupported()
  override def isDirectory: Boolean = false
  val lastModified: Long = System.currentTimeMillis
  override def input: InputStream = {
    pprint.log(flatFile.path)
    new ByteArrayInputStream(ffs.load(flatFile.path))
  }

  override def toByteArray: Array[Byte] = {
    ffs.load(flatJar, flatFile.path)
  }

  override def output: OutputStream = unsupported()
  override def iterator: Iterator[AbstractFile] = Iterator.empty
  override def lookupName(name: String, directory: Boolean): AbstractFile = null
  override def lookupNameUnchecked(
      name: String,
      directory: Boolean
  ): AbstractFile = null

  override def toString(): String = s"AFF($name)"
}
