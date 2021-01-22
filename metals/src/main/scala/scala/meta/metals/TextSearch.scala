package scala.meta.metals

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import scala.meta.internal.metals.PositionSyntax._
import scala.sys.process.Process
import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.StringBloomFilter
import scala.util.control.NonFatal
import scala.meta.inputs.Input
import scala.meta.inputs.Position
import scala.meta.internal.metals.Memory
import java.util.Scanner
import scala.meta.internal.io.PathIO
import scala.meta.internal.metals.Timer
import scala.meta.internal.metals.Time
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.concurrent.TrieMap
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer

object TextSearch {
  case class WordCharacter(start: Int, end: Int) {
    def isStart = start == end
  }
  def foreachWord(
      text: String,
      onWordCharacter: WordCharacter => Unit = _ => (),
      onWordComplete: WordCharacter => Unit = _ => ()
  ): Unit = {
    var i = 0
    val N = text.length()
    while (i < N) {
      val start = i
      while (i < N && Character.isLetterOrDigit(text.charAt(i))) {
        onWordCharacter(WordCharacter(start, i))
        i += 1
      }
      if (i > start) {
        onWordComplete(WordCharacter(start, i))
      }
      i += 1
    }
  }

  case class Bucket(bloom: StringBloomFilter, paths: Array[AbsolutePath])

  case class TextIndex(
      cwd: AbsolutePath,
      buckets: ArrayBuffer[Bucket] = ArrayBuffer.empty
  ) {
    def search(query: String): Unit = {
      val queryTimer = new Timer(Time.system)
      val files = new AtomicInteger()
      val hits = new AtomicInteger()
      buckets.par.foreach { bucket =>
        val bloom = bucket.bloom
        if (bloom.mightContain(query)) {
          bucket.paths.foreach { path =>
            val text = path.readText
            val input = Input.VirtualFile(path.toString(), text)
            val out = new StringBuilder()
            out.append(input.path).append("\n")
            var fileHits = 0
            foreachWord(
              text,
              _ => (),
              word => {
                val isMatch = text
                  .regionMatches(false, word.start, query, 0, query.length())
                if (isMatch) {
                  fileHits += 1
                  val pos = Position.Range(input, word.start, word.end)
                  out
                    .append(pos.startLine)
                    .append(": ")
                    .append(pos.lineContent)
                    .append("\n")
                }
              }
            )
            if (fileHits > 0) {
              files.incrementAndGet()
              hits.addAndGet(fileHits)
              println(out)
            }
          }
        }
      }
      println(
        s"query '$query' took $queryTimer to find $hits result(s) in $files file(s)"
      )
    }
  }

  object TextIndex {
    def fromDirectory(cwd: AbsolutePath): TextIndex = {
      val indexTimer = new Timer(Time.system)
      val index = TextIndex(cwd)
      val files = Process(
        "git ls-files",
        cwd = Some(cwd.toFile)
      ).!!.linesIterator.toArray
      val counter = new AtomicInteger()
      val paths = ArrayBuffer.empty[AbsolutePath]
      val bucketSize = 10000
      val fpr = 0.01
      var bloom = new StringBloomFilter(bucketSize)
      def flush(): Unit = {
        if (paths.nonEmpty) {
          index.buckets += Bucket(bloom, paths.toArray)
          bloom = new StringBloomFilter(bucketSize)
          paths.clear()
        }
      }
      files.foreach { file =>
        if (bloom.bloom.expectedFpp() > fpr) {
          flush()
        }
        val path = AbsolutePath(file)(cwd)
        try {
          if (path.isFile && Files.size(path.toNIO) < 1000000L) {
            paths += path
            val text = path.readText
            foreachWord(
              text,
              onWordCharacter = { word =>
                if (word.isStart) bloom.reset()
                bloom.putCharIncrementally(text.charAt(word.end))
              }
            )
            val i = counter.incrementAndGet()
            if (i % 10000 == 0) {
              println(f"[$i%3s/${files.size}] $indexTimer")
            }
          }
        } catch {
          case NonFatal(_) =>
        }
      }
      flush()
      index
    }
  }

  def main(args: Array[String]): Unit = {
    val cwd = args.toList match {
      case Nil => PathIO.workingDirectory
      case head :: _ => AbsolutePath(head)
    }
    val indexTimer = new Timer(Time.system)
    val index = TextIndex.fromDirectory(cwd)
    pprint.log(indexTimer)
    Memory.printFootprint(index)
    if (index.buckets.nonEmpty) {
      val bucket = index.buckets.maxBy(_.bloom.bloom.expectedFpp())
      println(
        s"worst bucket has expected false positive ratio " +
          s"${bucket.bloom.bloom.expectedFpp()}:\n${bucket.paths.mkString("\n")}"
      )
    }
    val scanner = new Scanner(System.in)
    try {
      while (true) {
        print("query> ")
        val line = scanner.nextLine()
        index.search(line)
      }
    } catch {
      case _: NoSuchElementException =>
    }
  }
}
