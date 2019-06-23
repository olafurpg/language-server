package scala.meta.internal.metals

import scala.collection.mutable
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import MetalsEnrichments._
import scala.meta.internal.io.FileIO
import java.nio.file.Files
import scala.meta.io.AbsolutePath
import java.{util => ju}
import java.net.URI
import java.net.JarURLConnection
import java.nio.file.Paths
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.mtags.Symbol
import java.util.concurrent.ScheduledExecutorService
import scala.collection.concurrent.TrieMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import org.eclipse.lsp4j.ExecuteCommandParams
import scala.meta.internal.io.PathIO
import scala.meta.internal.mtags.Mtags
import scala.meta.internal.semanticdb.Language.JAVA
import scala.meta.internal.semanticdb.Language.SCALA

class TreeViewProvider(
    workspace: () => AbsolutePath,
    languageClient: MetalsLanguageClient,
    buildTargets: BuildTargets,
    buildClient: () => ForwardingMetalsBuildClient,
    definitionIndex: OnDemandSymbolIndex,
    sh: ScheduledExecutorService
) {
  val ticks = TrieMap.empty[String, ScheduledFuture[_]]
  private val isVisible = TrieMap.empty[String, Boolean].withDefaultValue(false)
  def buildTargetsUri: String = "metals://build/targets"
  def buildJarsUri: String = "metals://build/jars"

  def visibilityDidChange(
      params: MetalsTreeViewVisibilityDidChangeParams
  ): Unit = {
    isVisible(params.viewId) = params.visible
    if (params.visible) {
      params.viewId match {
        case "compile" =>
          ticks(params.viewId) = sh.scheduleAtFixedRate(
            () => buildClient().tickBuildTreeView(),
            1,
            1,
            TimeUnit.SECONDS
          )
        case _ =>
      }
    } else {
      ticks.remove(params.viewId).foreach(_.cancel(false))
    }
  }

  private def toTreeViewNode(command: Command): MetalsTreeViewNode = {
    MetalsTreeViewNode(
      viewId = "commands",
      nodeUri = s"metals://command/${command.id}",
      label = command.title,
      command = MetalsCommand(command.title, command.id, command.description),
      tooltip = command.description
    )
  }

  val parents = ConcurrentHashSet.empty[String]

  def parent(
      params: MetalsTreeViewParentParams
  ): MetalsTreeViewParentResult = {
    MetalsTreeViewParentResult(
      params.viewId match {
        case "build" =>
          val uri =
            if (params.nodeUri.contains("!/")) {
              val dirname = PathIO.dirname(params.nodeUri)
              if (dirname.endsWith("!/")) {
                dirname.stripSuffix("!/").stripPrefix("jar:")
              } else dirname
            } else if (params.nodeUri.endsWith(".jar")) {
              buildJarsUri
            } else {
              null
            }
          if (uri != null) parents.add(uri)
          uri
        case _ =>
          null
      }
    )
  }

  def collapsedIfNotParent(uri: String): String =
    if (parents.contains(uri)) MetalsTreeItemCollapseState.expanded
    else MetalsTreeItemCollapseState.collapsed
  def children(
      params: MetalsTreeViewChildrenParams
  ): MetalsTreeViewChildrenResult = {
    val children: Array[MetalsTreeViewNode] = params.viewId match {
      case "commands" =>
        ServerCommands.all.map(toTreeViewNode).toArray
      case "build" =>
        Option(params.nodeUri) match {
          case None =>
            Array(
              toTreeViewNode(ServerCommands.ImportBuild),
              toTreeViewNode(ServerCommands.ConnectBuildServer),
              toTreeViewNode(ServerCommands.RunDoctor),
              MetalsTreeViewNode(
                "build",
                buildTargetsUri,
                "Targets",
                collapseState = collapsedIfNotParent(buildTargetsUri)
              ),
              MetalsTreeViewNode(
                "build",
                buildJarsUri,
                "Libraries",
                collapseState = collapsedIfNotParent(buildTargetsUri)
              )
            )
          case Some(uri) =>
            if (uri == buildTargetsUri) {
              buildTargets.all.toIterator.map { target =>
                MetalsTreeViewNode(
                  "build",
                  target.info.getId().getUri(),
                  target.info.getDisplayName,
                  collapseState = MetalsTreeItemCollapseState.collapsed
                )
              }.toArray
            } else if (uri == buildJarsUri) {
              val isVisited = mutable.Set.empty[AbsolutePath]
              val buf = Array.newBuilder[MetalsTreeViewNode]
              buildTargets.allWorkspaceJars.foreach { jar =>
                if (!isVisited(jar)) {
                  isVisited += jar
                  val uri = jar.toURI.toString()
                  buf += MetalsTreeViewNode(
                    "build",
                    uri,
                    jar.toNIO.getFileName().toString(),
                    tooltip = uri,
                    collapseState = collapsedIfNotParent(uri)
                  )
                }
              }
              val result = buf.result()
              ju.Arrays.sort(
                result,
                (a: MetalsTreeViewNode, b: MetalsTreeViewNode) =>
                  a.nodeUri.compareTo(b.nodeUri)
              )
              result
            } else if (uri.endsWith(".jar")) {
              listJar(uri.toAbsolutePath)
            } else if (uri.startsWith("jar:file") && uri.endsWith("/")) {
              val connection = URI
                .create(uri)
                .toURL()
                .openConnection()
                .asInstanceOf[JarURLConnection]
              val jar = AbsolutePath(
                Paths.get(connection.getJarFileURL().toURI())
              )
              FileIO.withJarFileSystem(
                jar,
                create = false,
                close = true
              ) { root =>
                list(root.resolve(connection.getEntryName()))
              }
            } else {
              buildTargets.info(new BuildTargetIdentifier(uri)) match {
                case None =>
                  Array.empty[MetalsTreeViewNode]
                case Some(info) =>
                  Array(
                    MetalsTreeViewNode(
                      "build",
                      uri + "/baseDirectory",
                      info.getBaseDirectory.toAbsolutePath.toString(),
                      tooltip = "Base directory"
                    )
                  )
              }
            }
        }
      case "compile" =>
        Option(params.nodeUri) match {
          case None =>
            Array(
              toTreeViewNode(ServerCommands.CascadeCompile),
              toTreeViewNode(ServerCommands.CancelCompile),
              buildClient().ongoingCompilationNode
            )
          case Some(uri) =>
            if (uri == buildClient().ongoingCompilationNode.nodeUri) {
              buildClient().ongoingCompilations
            } else {
              buildClient()
                .ongoingCompileNode(new BuildTargetIdentifier(uri))
                .toArray
            }
        }
      case _ => Array.empty
    }
    MetalsTreeViewChildrenResult(children)
  }

  def listJar(jar: AbsolutePath): Array[MetalsTreeViewNode] = {
    FileIO.withJarFileSystem(
      jar,
      create = false,
      close = true
    ) { root =>
      list(root).map { node =>
        if (node.isCollapsed) {
          node.copy(collapseState = MetalsTreeItemCollapseState.expanded)
        } else node
      }
    }
  }

  private def containsClassfile(dir: Path): Boolean = {
    var foundClassfile = false
    Files.walkFileTree(
      dir,
      new SimpleFileVisitor[Path] {
        override def preVisitDirectory(
            dir: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          if (foundClassfile) FileVisitResult.SKIP_SUBTREE
          else FileVisitResult.CONTINUE
        }
        override def visitFile(
            file: Path,
            attrs: BasicFileAttributes
        ): FileVisitResult = {
          if (file.isClassfile) {
            foundClassfile = true
          }
          if (foundClassfile) FileVisitResult.SKIP_SIBLINGS
          else FileVisitResult.CONTINUE
        }
      }
    )
    foundClassfile
  }

  private def listFiles(path: Path): collection.Seq[Path] = {
    val ls = Files.list(path)
    try {
      ls.iterator()
        .asScala
        .filter { path =>
          if (Files.isDirectory(path)) containsClassfile(path)
          else path.isClassfile
        }
        .toBuffer
    } finally {
      ls.close()
    }
  }
  def list(root: AbsolutePath): Array[MetalsTreeViewNode] = {
    def visit(
        path: Path,
        filenamePrefix: List[String] = Nil
    ): List[MetalsTreeViewNode] = {
      val uri = path.toUri.toString()
      if (uri.endsWith(".class") && uri.contains('$')) Nil
      else {
        val (command, isDefinition) =
          if (uri.endsWith(".class")) {
            val symbol = path
              .toString()
              .stripPrefix("/")
              .stripSuffix(".class") + "#"
            Some(
              MetalsCommand(
                "Go to Definition",
                "metals.goto",
                symbol,
                Array(symbol)
              )
            ) -> definitionIndex.definition(Symbol(symbol)).isDefined
          } else {
            (None, true)
          }
        if (!isDefinition) Nil
        else {
          val isDirectory = Files.isDirectory(path)
          val children =
            if (isDirectory) listFiles(path)
            else Nil
          val filename = path.getFileName().toString()
          if (false && isDirectory &&
            children.size == 1 &&
            children.forall(child => Files.isDirectory(path))) {
            visit(children.head, filename :: filenamePrefix)
          } else if (isDirectory && children.isEmpty) {
            Nil
          } else {
            val collapseState =
              if (parents.contains(uri)) MetalsTreeItemCollapseState.expanded
              else if (isDirectory) {
                if (children.size == 1) MetalsTreeItemCollapseState.expanded
                else MetalsTreeItemCollapseState.collapsed
              } else {
                MetalsTreeItemCollapseState.none
              }
            MetalsTreeViewNode(
              "build",
              uri,
              (filename :: filenamePrefix).reverse.mkString,
              tooltip = uri,
              collapseState = collapseState,
              command = command.orNull
            ) :: Nil
          }
        }
      }
    }
    listFiles(root.toNIO).iterator
      .flatMap(path => visit(path))
      .toArray
      .sortBy(!_.isNoCollapse)
  }

  private def findClassfile(relativeUris: List[String]): Option[URI] = {
    if (relativeUris.isEmpty) return None
    val jars = buildTargets.allWorkspaceJars
    var result = Option.empty[URI]
    while (jars.hasNext && result == None) {
      val jar = jars.next()
      FileIO.withJarFileSystem(jar, create = false, close = false) { root =>
        relativeUris.foreach { relativeUri =>
          val source = root.resolve(relativeUri)
          if (source.isFile) {
            result = Some(source.toURI)
          }
        }
      }
    }
    result
  }

  def didFocusReadonly(path: AbsolutePath): Unit = {
    if (isVisible("build")) {
      val classfiles: List[String] = path.toLanguage match {
        case JAVA =>
          val uri = path
            .toRelative(workspace().resolve(Directories.readonly))
            .resolveSibling(_.replaceAllLiterally(".java", ".class"))
            .toURI(false)
            .toString
          uri :: Nil
        case SCALA =>
          val input = path.toInput
          val toplevels = Mtags.toplevels(input)
          toplevels.map { toplevel =>
            import scala.meta.internal.semanticdb.Scala._
            toplevel.owner + toplevel.desc.name.value + ".class"
          }
        case _ => Nil
      }
      pprint.log(classfiles)
      val classfileUri = findClassfile(classfiles)
      pprint.log(classfileUri)
      classfileUri.foreach { uri =>
        languageClient.metalsExecuteClientCommand(
          new ExecuteCommandParams(
            ClientCommands.RevealTreeView.id,
            List(MetalsRevealTreeViewParams("build", uri.toString()): Object).asJava
          )
        )
      }
    }
  }
}
