package scala.meta.internal.metals.debug.protocol

import ch.epfl.scala.{bsp4j => b}

final case class LaunchParameters(
    cwd: String,
    mainClass: String,
    buildTarget: b.BuildTargetIdentifier
)
