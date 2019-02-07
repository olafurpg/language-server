package scala.meta.internal.pc

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class AbstractFlatJar(val flatJar: FlatJar, ffs: FlatFileSystem) {
  val root = new AbstractFlatDir(flatJar.uri, ArrayBuffer.empty)
  val dirs: mutable.HashMap[String, AbstractFlatDir] =
    mutable.HashMap[String, AbstractFlatDir]("" -> root)

  build()

  def build(): Unit = {
    def findParent(path: Seq[String]): AbstractFlatDir = {
      val dir = path.mkString("/")
      dirs.get(dir) match {
        case Some(absDir) =>
          absDir
        case None =>
          val parent = findParent(path.dropRight(1))
          val newDir = new AbstractFlatDir(dir.drop(1))
          dirs.put(dir, newDir)
          parent.children.append(newDir)
          newDir
      }
    }

    flatJar.files.foreach { file =>
      val path = "" +: file.path.split('/')
      val parent = findParent(path.dropRight(1))
      val newFile = new AbstractFlatFile(file, flatJar, ffs)
      parent.children.append(newFile)
    }
  }
}
