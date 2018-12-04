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
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath
import scala.meta.internal.io._
import scala.meta.internal.mtags.Mtags
import scala.meta.internal.semanticdb.TextDocument
import scala.meta.io.Classpath
import scala.reflect.NameTransformer
import scala.meta.internal.mtags.MtagsEnrichments._
import MetalsEnrichments._
import scala.meta.internal.mtags.Symbol
import scala.meta.internal.mtags.GlobalSymbolIndex
import scala.meta.internal.semanticdb.Scala
import scala.meta.internal.semanticdb.Scala.Descriptor
import scala.meta.internal.semanticdb.Scala.Symbols
import scala.meta.internal.semanticdb.SymbolInformation.Kind

final class WorkspaceSymbolProvider(
    mtags: Mtags,
    index: GlobalSymbolIndex,
    buildTargets: BuildTargets,
    workspace: AbsolutePath
) {
  case class CachedFile(
      source: AbsolutePath,
      readonly: AbsolutePath,
      semanticdb: TextDocument
  )
  private val cache = TrieMap.empty[AbsolutePath, CachedFile]
  def search(query: String): util.List[SymbolInformation] = {
    if (query.trim.isEmpty) return null
    val classpath = mutable.Set.empty[AbsolutePath]
    classpath ++= JdkClasspath.bootClasspath.entries
    buildTargets.all.foreach { target =>
      classpath ++= target.scalac.classpath.entries
    }
    val results = new util.ArrayList[SymbolInformation]()
    Classpath(classpath.toList).foreach { root =>
      Files.walkFileTree(
        root.path.toNIO,
        new SimpleFileVisitor[Path] {
          override def visitFile(
              file: Path,
              attrs: BasicFileAttributes
          ): FileVisitResult = {
            if (PathIO.extension(file) == "class") {
              val filename = file.getFileName.toString
              if (isSub(query, filename)) {
                val dollar = filename.indexOf('$')
                val symname =
                  if (dollar < 0) filename.stripSuffix(".class")
                  else filename.substring(0, dollar)
                val owner =
                  if (root.enclosingJar.isDefined) file.getParent.toString
                  else root.pathOnDisk.toNIO.getParent.toString
                val toplevel =
                  Symbols.Global(
                    owner.stripPrefix("/") + "/",
                    Descriptor.Type(symname)
                  )
                val d = index.definition(Symbol(toplevel))
                d.foreach {
                  defn =>
                    val path = defn.path
                    val cached = cache.getOrElseUpdate(
                      path, {
                        val input = path.toInput
                        val readonly = path.toFileOnDisk(workspace)
                        val semanticdb = mtags.index(path.toLanguage, input)
                        CachedFile(path, readonly, semanticdb)
                      }
                    )
                    for {
                      sym <- cached.semanticdb.symbols
                      if (sym.kind match {
                        case Kind.CLASS | Kind.INTERFACE | Kind.TRAIT |
                            Kind.OBJECT =>
                          true
                        case _ => false
                      })
                      if isSub(query, sym.displayName, isEncode = false)
                    } {
                      for {
                        occ <- cached.semanticdb.occurrences
                          .find(_.symbol == sym.symbol)
                        range <- occ.range
                      } {
                        val location = new l.Location(
                          cached.readonly.toURI.toString,
                          new l.Range(
                            new l.Position(
                              range.startLine,
                              range.startCharacter
                            ),
                            new l.Position(
                              range.endLine,
                              range.endCharacter
                            )
                          )
                        )
                        results.add(
                          new l.SymbolInformation(
                            sym.displayName,
                            sym.kind.toLSP,
                            location,
                            sym.symbol
                          )
                        )
                      }
                    }
                }
              }
            }
            FileVisitResult.CONTINUE
          }
        }
      )
    }
    results.sort((a, b) => Integer.compare(a.getName.length, b.getName.length))
    results
  }

  def isSub(
      needle: String,
      haystack: String,
      isEncode: Boolean = true
  ): Boolean = {
    if (haystack.endsWith("$.class")) {
      false
    } else {
      isSubstring(
        needle,
        needle.length - 1,
        haystack,
        haystack.length - "$.class".length,
        isEncode
      )
    }
  }

  def isTerminal(ch: Char): Boolean = ch match {
    case '/' | '$' => true
    case _ => false
  }

  @tailrec
  def isSubstring(
      needle: String,
      n: Int,
      haystack: String,
      h: Int,
      isEncode: Boolean,
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
      if (isEncode && Character.isLetterOrDigit(ch)) {
        if (needle.charAt(n) == haystack.charAt(h)) {
          isSubstring(needle, n - 1, haystack, h - 1, isEncode)
        } else {
          isSubstring(needle, n, haystack, h - 1, isEncode)
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
          isSubstring(needle, n - 1, haystack, h - encoded.length, isEncode)
        } else {
          isSubstring(needle, n, haystack, h - 1, isEncode, encoded)
        }
      }
    }
  }
}
