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

class TreeViewProvider(
    buildTargets: BuildTargets,
    buildClient: () => ForwardingMetalsBuildClient,
    definitionIndex: OnDemandSymbolIndex,
    sh: ScheduledExecutorService
) {
  val ticks = TrieMap.empty[String, ScheduledFuture[_]]
  def buildTargetsUri: String = "metals:build/targets"
  def buildJarsUri: String = "metals:build/jars"

  def visibilityDidChange(
      params: MetalsTreeViewVisibilityDidChangeParams
  ): Unit = {
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
      nodeUri = command.id,
      label = command.title,
      command = MetalsCommand(command.title, command.id, command.description),
      tooltip = command.description
    )
  }

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
                isCollapsible = true
              ),
              MetalsTreeViewNode(
                "build",
                buildJarsUri,
                "Libraries",
                isCollapsible = true
              )
            )
          case Some(uri) =>
            if (uri == buildTargetsUri) {
              buildTargets.all.toIterator.map { target =>
                MetalsTreeViewNode(
                  "build",
                  target.info.getId().getUri(),
                  target.info.getDisplayName,
                  isCollapsible = true
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
                    isCollapsible = true
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
      list(root)
    }
  }

  def list(root: AbsolutePath): Array[MetalsTreeViewNode] = {
    val ls = Files.list(root.toNIO)
    try {
      ls.iterator()
        .asScala
        .flatMap { path =>
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
              MetalsTreeViewNode(
                "build",
                uri,
                path.getFileName().toString(),
                tooltip = uri,
                isCollapsible = Files.isDirectory(path),
                command = command.orNull
              ) :: Nil
            }
          }
        }
        .toArray
    } finally {
      ls.close()
    }
  }
}
