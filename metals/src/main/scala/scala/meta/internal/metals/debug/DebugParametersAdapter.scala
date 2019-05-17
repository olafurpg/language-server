package scala.meta.internal.metals.debug

import ch.epfl.scala.{bsp4j => b}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.debug.{protocol => jvm}
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.CompilationScheduler
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.protocol.LaunchParameters
import scala.meta.internal.metals.debug.{protocol => metals}

private[debug] final class DebugParametersAdapter(
    compilationScheduler: CompilationScheduler,
    buildTargets: BuildTargets
)(implicit ec: ExecutionContext) {
  def adapt(
      params: metals.LaunchParameters
  ): Future[jvm.LaunchParameters] = {
    val buildTarget = params.buildTarget
    for {
      result <- compilationScheduler.cascadeCompile(buildTarget)
      _ <- Future(verify(result))
    } yield adapt(params, buildTarget)
  }

  private def verify(result: b.CompileResult): Unit =
    if (result.failed) throw new IllegalStateException("Compilation failed")

  private def adapt(
      params: LaunchParameters,
      buildTarget: b.BuildTargetIdentifier
  ): jvm.LaunchParameters = {
    val classpath = classpathOf(buildTarget)
    jvm.LaunchParameters(
      params.cwd,
      params.mainClass,
      classpath
    )
  }

  private def classpathOf(buildTarget: b.BuildTargetIdentifier): Array[String] =
    for {
      dependency <- buildTargets.scalacOptions(buildTarget).toArray
      classpath <- dependency.getClasspath.asScala
    } yield classpath
}
