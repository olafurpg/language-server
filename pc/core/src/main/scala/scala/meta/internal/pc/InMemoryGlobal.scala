package scala.meta.internal.pc

// This source file has been adapted from https://github.com/scalafiddle/scalafiddle-core/blob/4ecab2265cab4e81e78fff72069e269a3af4ee8c/compiler-server/src/main/scala-2.12/scalafiddle/compiler/GlobalInitCompat.scala
// See NOTICE.md for original license.

import java.net.URL
import java.net.URLClassLoader
import scala.collection.mutable
import scala.meta.internal.metals.ClasspathSearch
import scala.meta.pc.SymbolIndexer
import scala.reflect.io
import scala.tools.nsc
import scala.tools.nsc.Settings
import scala.tools.nsc.classpath.AggregateClassPath
import scala.tools.nsc.classpath._
import scala.tools.nsc.interactive.InteractiveAnalyzer
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.typechecker.Analyzer
import scala.util.Try

object InMemoryGlobal {

  def createInteractive(
      settings: Settings,
      reporter: StoreReporter,
      libs: Seq[io.AbstractFile],
      indexer: SymbolIndexer,
      search: ClasspathSearch
  ): PresentationCompiler = {
    val cp = new AggregateClassPath(libs.map(buildClassPath))
    new PresentationCompiler(settings, reporter, indexer, search) { g =>
      override def classPath = cp

      override lazy val platform: ThisPlatform = new GlobalPlatform {
        override val global = g
        override val settings = g.settings
        override def classPath = cp
      }

      override lazy val analyzer = new {
        val global: g.type = g
      } with InteractiveAnalyzer {
        val cl = inMemClassloader(libs)

        override def findMacroClassLoader() = cl
      }
    }
  }

  def createNormal(
      settings: Settings,
      reporter: StoreReporter,
      libs: Seq[io.AbstractFile]
  ): nsc.Global = {
    val cp = new AggregateClassPath(libs.map(buildClassPath))
    val cl = inMemClassloader(libs)

    new nsc.Global(settings, reporter) { g =>
      override def classPath = cp

      override lazy val platform: ThisPlatform = new GlobalPlatform {
        override val global = g
        override val settings = g.settings
        override def classPath = cp
      }

      override lazy val analyzer = new {
        val global: g.type = g
      } with Analyzer {
        override def findMacroClassLoader() = cl
      }
    }
  }

  private def inMemClassloader(libs: Seq[io.AbstractFile]): ClassLoader = {
    new URLClassLoader(new Array[URL](0), this.getClass.getClassLoader) {
      private val classCache = mutable.Map.empty[String, Option[Class[_]]]

      override def findClass(name: String): Class[_] = {
        def findClassInLibs(): Option[AbstractFile] = {
          val parts = name.split('.')
          libs
            .map(dir => {
              Try {
                parts
                  .dropRight(1)
                  .foldLeft[AbstractFile](dir)(
                    (parent, next) => parent.lookupName(next, directory = true)
                  )
                  .lookupName(parts.last + ".class", directory = false)
              } getOrElse null
            })
            .find(_ != null)
        }

        val res = classCache.getOrElseUpdate(
          name,
          findClassInLibs().map { f =>
            val data = f.toByteArray
            this.defineClass(name, data, 0, data.length)
          }
        )
        res match {
          case None =>
            scribe.error("Not Found Class " + name)
            throw new ClassNotFoundException()
          case Some(cls) =>
            cls
        }
      }

      override def close(): Unit = {}
    }
  }

  private final def lookupPath(
      base: AbstractFile
  )(pathParts: Seq[String], directory: Boolean): AbstractFile = {
    var file: AbstractFile = base
    for (dirPart <- pathParts.init) {
      file = file.lookupName(dirPart, directory = true)
      if (file == null)
        return null
    }

    file.lookupName(pathParts.last, directory = directory)
  }

  private def buildClassPath(absFile: AbstractFile): VirtualDirectoryClassPath =
    new VirtualDirectoryClassPath(new VirtualDirectory(absFile.name, None) {
      override def iterator: Iterator[AbstractFile] = absFile.iterator

      override def lookupName(name: String, directory: Boolean): AbstractFile =
        absFile.lookupName(name, directory)

      override def subdirectoryNamed(name: String): AbstractFile =
        absFile.subdirectoryNamed(name)
    }) {
      override def getSubDir(packageDirName: String): Option[AbstractFile] = {
        Option(lookupPath(absFile)(packageDirName.split('/'), directory = true))
      }

      override def findClassFile(className: String): Option[AbstractFile] = {
        val relativePath = FileUtils.dirPath(className) + ".class"
        Option(lookupPath(absFile)(relativePath.split('/'), directory = false))
      }
    }
}
