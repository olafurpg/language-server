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

class TreeViewProvider(
    buildTargets: BuildTargets,
    buildClient: () => ForwardingMetalsBuildClient,
    definitionIndex: OnDemandSymbolIndex
) {
  def buildTargetsUri: String = "metals:build/targets"
  def buildJarsUri: String = "metals:build/jars"
  def children(
      params: MetalsTreeViewChildrenParams
  ): MetalsTreeViewChildrenResult = {
    val children: Array[MetalsTreeViewNode] = params.viewId match {
      case "commands" =>
        ServerCommands.all.map { command =>
          MetalsTreeViewNode(
            "commands",
            command.id,
            command.title,
            command =
              MetalsCommand(command.title, command.id, command.description)
          )
        }.toArray
      case "compile" =>
        Option(params.nodeUri) match {
          case None =>
            buildClient().toplevelTreeNodes
          case Some(uri) =>
            if (uri == buildClient().recentCompilationNode.nodeUri) {
              buildClient().recentCompilations
            } else if (uri == buildClient().ongoingCompilationNode.nodeUri) {
              buildClient().ongoingCompilations
            } else {
              buildClient()
                .ongoingCompileNode(new BuildTargetIdentifier(uri))
                .toArray
            }
        }
      case "build" =>
        Option(params.nodeUri) match {
          case None =>
            Array(
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
