package scala.meta.internal.metals

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util
import java.util.jar.JarFile
import org.eclipse.{lsp4j => l}
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import scala.annotation.tailrec
import scala.collection.mutable
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath
import scala.meta.io.Classpath
import scala.meta.internal.io._
import scala.reflect.NameTransformer

class WorkspaceSymbolProvider(
    buildTargets: BuildTargets,
    workspace: AbsolutePath
) {
  def dummyLocation: Location = {
    new Location(
      workspace.resolve("build.sbt").toURI.toString,
      new l.Range(
        new l.Position(0, 0),
        new l.Position(0, 0)
      )
    )
  }
  def symbol(query: String): util.List[SymbolInformation] = {
    val classpath = mutable.Set.empty[AbsolutePath]
    buildTargets.all.foreach { target =>
      classpath ++= target.scalac.classpath.entries
    }
    val results = new util.ArrayList[SymbolInformation]()
    def expandDirectory(dir: AbsolutePath): Unit = {
      Files.walkFileTree(
        dir.toNIO,
        new SimpleFileVisitor[Path] {
          override def visitFile(
              file: Path,
              attrs: BasicFileAttributes
          ): FileVisitResult = {
            if (PathIO.extension(file) == "class") {
              val relpath = AbsolutePath(file).toRelative(dir)
              val reluri = relpath.toURI(false).toString
              if (isSubstring(query, reluri)) {
                results.add(
                  new SymbolInformation(
                    file.getFileName.toString,
                    SymbolKind.Class,
                    dummyLocation,
                    reluri
                  )
                )
              }
            }
            FileVisitResult.CONTINUE
          }
        }
      )
    }

    def expandJar(jarpath: AbsolutePath): Unit = {
      val file = jarpath.toFile
      val jar = new JarFile(file)
      try {
        val entries = jar.entries()
        while (entries.hasMoreElements) {
          val element = entries.nextElement()
          val name = element.getName
          if (!name.startsWith("META-INF")) {
            if (isSubstring(query, name)) {
              results.add(
                new SymbolInformation(
                  PathIO.basename(name),
                  SymbolKind.Class,
                  dummyLocation,
                  name
                )
              )
            }
          }
        }
      } finally {
        jar.close()
      }
    }
    classpath.foreach { entry =>
      if (entry.isFile && PathIO.extension(entry.toNIO) == "jar") {
        expandJar(entry)
      } else if (entry.isDirectory) {
        expandDirectory(entry)
      } else {
        ()
      }
    }
    results
  }

  def isSubstring(needle: String, haystack: String): Boolean = {
    if (haystack.endsWith("$.class")) {
      false
    } else {
      isSubstring(needle, needle.length - 1, haystack, haystack.length - 1)
    }
  }

  def isTerminal(ch: Char): Boolean = ch match {
    case '/' | '$' => true
    case _ => false
  }

  @tailrec
  final def isSubstring(
      needle: String,
      n: Int,
      haystack: String,
      h: Int,
      encodedChar: String = null
  ): Boolean = {
    if (n < 0) {
      true
    } else if (h < 0) {
      false
    } else if (isTerminal(haystack.charAt(h))) {
      false
    } else {
      val ch = needle.charAt(n)
      if (Character.isLetterOrDigit(ch)) {
        if (needle.charAt(n) == haystack.charAt(h)) {
          isSubstring(needle, n - 1, haystack, h - 1)
        } else {
          isSubstring(needle, n, haystack, h - 1)
        }
      } else {
        val encoded =
          if (encodedChar == null) {
            NameTransformer.encode(ch.toString)
          } else {
            encodedChar
          }
        val from = h - encoded.length
        if (haystack.startsWith(encoded, from)) {
          isSubstring(needle, n - 1, haystack, h - encoded.length)
        } else {
          isSubstring(needle, n, haystack, h - 1, encoded)
        }
      }
    }
  }
}
