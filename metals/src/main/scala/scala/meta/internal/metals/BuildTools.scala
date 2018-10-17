package scala.meta.internal.metals

import scala.meta.io.AbsolutePath

final class BuildTools(workspace: AbsolutePath) {
  def isBloop: Boolean = workspace.resolve(".bloop").isDirectory
  def isSbt: Boolean = workspace.resolve("build.sbt").isFile
  def isMill: Boolean = workspace.resolve("build.sc").isFile
  def isGradle: Boolean = workspace.resolve("build.gradle").isFile
  def isMaven: Boolean = workspace.resolve("pom.xml").isFile
  def isPants: Boolean = workspace.resolve("pants.ini").isFile
  def isBazel: Boolean = workspace.resolve("WORKSPACE").isFile
  import BuildTool._
  def asSbt: Option[Sbt] =
    if (isSbt) Some(SbtVersion(workspace))
    else None
  def all: List[BuildTool] = {
    val buf = List.newBuilder[BuildTool]
    if (isBloop) buf += Bloop
    buf ++= asSbt.toList
    if (isMill) buf += Mill
    if (isGradle) buf += Gradle
    if (isMaven) buf += Maven
    if (isPants) buf += Pants
    if (isBazel) buf += Bazel
    buf.result()
  }
  override def toString: String = s"BuildTool(${all.mkString(", ")})"
}
