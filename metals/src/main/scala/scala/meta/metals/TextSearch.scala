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

  case class TextIndex(
      cwd: AbsolutePath,
      cache: ConcurrentHashMap[Path, StringBloomFilter] =
        new ConcurrentHashMap()
  ) {
    lazy val keys = cache.keys().asScala.toArray.par
    def put(path: AbsolutePath): Unit = {
      try {
        val attr =
          Files.readAttributes(path.toNIO, classOf[BasicFileAttributes])
        if (!attr.isRegularFile()) return
        if (attr.size() > 1000000L) return // Skip files with size over 1mb
        val text = path.readText
        val words = ListBuffer.empty[WordCharacter]
        foreachWord(
          text,
          onWordComplete = word => {
            words += word
          }
        )
        val sizeEstimate = words.foldLeft(0) { case (accum, next) =>
          accum + (next.end - next.start)
        }
        val bloom = new StringBloomFilter(sizeEstimate)
        words.foreach { word =>
          bloom.reset()
          word.start.until(word.end).foreach { i =>
            bloom.putCharIncrementally(text.charAt(i))
          }
        }
        cache.put(path.toNIO, bloom)
      } catch {
        case NonFatal(e) =>
          println(s"error: $path\n$e")
          e.printStackTrace()
      }
    }
    def search(query: String): Unit = {
      val queryTimer = new Timer(Time.system)
      val files = new AtomicInteger()
      val hits = new AtomicInteger()
      keys.foreach { key =>
        val bloom = cache.get(key)
        if (bloom.mightContain(query)) {
          val text = AbsolutePath(key)(cwd).readText
          val input = Input.VirtualFile(key.toString(), text)
          val out = new StringBuilder()
          out.append(key.toString()).append("\n")
          var fileHits = 0
          foreachWord(
            text,
            _ => (),
            word => {
              if (
                text.regionMatches(false, word.start, query, 0, query.length())
              ) {
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
      println(
        s"query '$query' took $queryTimer to find $hits result(s) in $files file(s)"
      )
    }
  }
  def main(args: Array[String]): Unit = {
    val cwd = args.toList match {
      case Nil => PathIO.workingDirectory
      case head :: _ => AbsolutePath(head)
    }
    val index = TextIndex(cwd)
    val indexTimer = new Timer(Time.system)
    val files = Process(
      "git ls-files",
      cwd = Some(cwd.toFile)
    ).!!.linesIterator.toArray.par
    val counter = new AtomicInteger()
    files.foreach { file =>
      val i = counter.incrementAndGet()
      if (i % 10000 == 0) {
        println(f"[$i%3s/${files.size}] $indexTimer")
      }
      index.put(AbsolutePath(file)(cwd))
    }
    pprint.log(indexTimer)
    Memory.printFootprint(index)
    val triemap = TrieMap[Path, StringBloomFilter]() ++= index.cache.asScala
    if (triemap.nonEmpty) {
      val (path, bloom) = triemap.maxBy(_._2.bloom.expectedFpp())
      println(
        s"file $path has expected false positive ratio ${bloom.bloom.expectedFpp()}"
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
