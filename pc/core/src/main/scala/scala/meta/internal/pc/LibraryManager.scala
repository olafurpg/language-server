package scala.meta.internal.pc

import java.nio.file.Files
import java.nio.file.Path
import scala.collection.concurrent.TrieMap
import scala.meta.internal.metals.RecursivelyDelete
import scala.meta.io.AbsolutePath
import scala.reflect.io.AbstractFile

case class LibraryManager(workspace: Path) {

  val inmemory = workspace.resolve("inmemory")
  RecursivelyDelete(AbsolutePath(inmemory))
  Files.createDirectories(inmemory)
  val cache = TrieMap.empty[String, AbstractFlatJar]

  def buildFlatFileSystem(jars: Seq[Path]): Seq[AbstractFile] = {
    val toBuild = jars.filterNot(p => cache.contains(p.toUri.toString))
//    // acquire an exclusive lock to prevent others from updating the FFS at the same time
//    val lockFile = inmemory.resolve("ffs.lck").toFile
//    val lockChannel = new RandomAccessFile(lockFile, "rw").getChannel
//    var lock: FileLock = null
//    try {
//      while (lock == null) {
//        try {
//          lock = lockChannel.tryLock()
//        } catch {
//          case _: OverlappingFileLockException =>
//            lock = null
//        }
//        if (lock == null) {
//          scribe.info("\rAcquiring lock...")
//          Thread.sleep(1000)
//        }
//      }
    if (toBuild.nonEmpty) {
      val ffs = FlatFileSystem.build(inmemory, toBuild)
      ffs.jars.foreach { jar =>
        cache(jar.uri) = new AbstractFlatJar(jar, ffs)
      }
    }
    jars.map(jar => cache(jar.toUri.toString).root)
//    } finally {
//      lock.release()
//      lockChannel.close()
//    }
  }

}
