package scala.meta.internal.pc

import com.google.common.io.Files
import com.google.gson.Gson
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.{Files => JFiles}
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.xerial.snappy.Snappy
import scala.meta.internal.io.InputStreamIO
import xerial.larray._
import xerial.larray.mmap.MMapMode

object FlatFileSystem {
  val indexname = "index.json"
  def build(location: Path, jars: Seq[Path]): FlatFileSystem = {
    // if metadata already exists, read it in
    val existingJars: Seq[FlatJar] =
      if (location.resolve(indexname).toFile.exists()) readMetadata(location)
      else Seq.empty[FlatJar]

    val newJars =
      jars.filterNot(p => existingJars.exists(_.uri == p.toUri.toString))

    // make location path
    JFiles.createDirectories(location)

    val dataFile = location.resolve("data").toFile
    val fos = new FileOutputStream(dataFile, true)
    var offset = dataFile.length()

    // read through all new JARs, append contents to data and create metadata
    val addedJars = newJars.map { jar =>
      val name = jar.toUri.toString
      val fis = JFiles.newInputStream(jar)
      val jarStream = new ZipInputStream(fis)
      try {
        scribe.debug(s"Extracting JAR $name")
        val entries = Iterator
          .continually(jarStream.getNextEntry)
          .takeWhile(_ != null)
          .filter(validFile)

        val files = entries.map { entry =>
          // read and compress the file
          val content = InputStreamIO.readBytes(jarStream)
          val compressed = Snappy.compress(content)
          fos.write(compressed)
          val ff =
            FlatFile(entry.getName, offset, compressed.length, content.length)
          offset += compressed.length
          ff
        }.toList
        FlatJar(name, files.toArray)
      } finally {
        jarStream.close()
      }
    }
    fos.close()

    val finalJars = existingJars ++ addedJars
    val json = gson.toJson(finalJars.toArray)
    Files.write(
      json,
      location.resolve(indexname).toFile,
      StandardCharsets.UTF_8
    )

    val data = LArray.mmap(location.resolve("data").toFile, MMapMode.READ_ONLY)
    new FlatFileSystem(data, finalJars, createIndex(finalJars))
  }

  private val gson = new Gson()

//  def apply(location: Path): FlatFileSystem = {
//    location.toFile.mkdirs()
//    val jars = readMetadata(location)
//    val index: Map[String, FlatFile] = createIndex(jars)
//    val data = LArray.mmap(location.resolve("data").toFile, MMapMode.READ_ONLY)
//    new FlatFileSystem(data, jars, index)
//  }

  private def createIndex(jars: Seq[FlatJar]): Map[String, FlatFile] = {
    jars.flatMap(_.files.map(file => (file.path, file)))(collection.breakOut)
  }

  private def readMetadata(location: Path): Seq[FlatJar] = {
    val text = Files.toString(
      location.resolve(indexname).toFile,
      StandardCharsets.UTF_8
    )
    gson.fromJson[Array[FlatJar]](text, classOf[Array[FlatJar]]).toSeq
  }

  private val validExtensions = Set("class")
  private def validFile(entry: ZipEntry) = {
    !entry.isDirectory && validExtensions.contains(
      Files.getFileExtension(entry.getName)
    )
  }

}

class FlatFileSystem(
    data: MappedLByteArray,
    val jars: Seq[FlatJar],
    val index: Map[String, FlatFile]
) {

  def exists(path: String): Boolean = index.contains(path)

  def load(flatJar: FlatJar, path: String): Array[Byte] = {
    load(jars.find(_.uri == flatJar.uri).get.files.find(_.path == path).get)
  }

  def load(path: String): Array[Byte] = {
    load(index(path))
  }

  def load(file: FlatFile): Array[Byte] = {
    val address = data.address + file.offset
    val content =
      LArray.of[Byte](file.origSize).asInstanceOf[RawByteArray[Byte]]
    Snappy.rawUncompress(address, file.compressedSize, content.address)
    val bytes = Array.ofDim[Byte](file.origSize)
    content.writeToArray(0, bytes, 0, file.origSize)
    content.free
    bytes
  }

  def filter(f: Set[String]): FlatFileSystem = {
    val newJars = jars.filter(j => f.contains(j.uri))
    new FlatFileSystem(data, newJars, FlatFileSystem.createIndex(newJars))
  }
}
