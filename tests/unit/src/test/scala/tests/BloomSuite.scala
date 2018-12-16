package tests

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.mutable
import scala.meta.internal.io.PathIO
import scala.meta.internal.metals.Memory
import scala.meta.internal.metals.Time
import scala.meta.internal.metals.Timer
import scala.meta.internal.semanticdb.TextDocuments

object BloomSuite extends BaseSuite {
  test("basic") {
    var files = 0
    var i = 0
    pprint.log(PathIO.workingDirectory)
    pprint.log(BuildInfo.sourceroot)
    val elapsed = new Timer(Time.system)
    val blooms = mutable.Map.empty[Path, BloomFilter[CharSequence]]
    val root =
      BuildInfo.sourceroot.toPath
        .resolveSibling("frontend")
//        .resolve("server")
        .resolve(".bloop")
    Files.walkFileTree(
      root,
      new SimpleFileVisitor[Path] {
        override def visitFile(
            file: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          if (PathIO.extension(file) == "semanticdb") {
            val td = TextDocuments.parseFrom(Files.readAllBytes(file))
            val count = td.documents.foldLeft(0)(_ + _.occurrences.length)
            val bloom = BloomFilter.create(
              Funnels.stringFunnel(StandardCharsets.UTF_8),
              Integer.valueOf(count * 2),
              0.01
            )
            blooms(file) = bloom
            files += 1
            for {
              d <- td.documents
              o <- d.occurrences
            } {
              i += 1
              bloom.put(o.symbol)
            }
            pprint.log(file)
          }
          super.visitFile(file, attrs)
        }
      }
    )
    pprint.log(elapsed)
    def contains(s: String): Int = {
      val timer = new Timer(Time.system)
      val x = blooms.values.count(_.mightContain(s))
      pprint.log(s -> timer)
      x
    }

    pprint.log(i)
    pprint.log(files)
    pprint.log(i / files)
    Memory.printFootprint(blooms)
    pprint.log(contains("scala/Int#"))
    pprint.log(contains("java/lang/String#"))
    pprint.log(contains("scala/Predef."))
    pprint.log(contains("scala/Predef.String#"))
    pprint.log(contains("asdasdasdkjlhwer"))
//    blooms.values.foreach(b => pprint.log(b.approximateElementCount()))

  }
}
