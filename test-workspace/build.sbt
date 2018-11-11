import sbtcrossproject.{crossProject, CrossType}

inThisBuild(List(
  scalaVersion := "2.12.7",
))

// Comment

libraryDependencies ++= List(
  "org.typelevel" %% "cats-core" % "1.4.0",
  "com.lihaoyi" %% "ujson" % "0.6.5",
  "com.lihaoyi" %% "sourcecode" % "0.1.4",
  "org.scalameta" %% "scalameta" % "4.0.0",
  "org.scalatest" %% "scalatest" % "3.0.5"
)

lazy val cross = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
lazy val crossJS = cross.js
lazy val crossJVM = cross.jvm
