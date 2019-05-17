package scala.meta.internal.metals
import ch.epfl.scala.{bsp4j => b}
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath

final class CompilationScheduler(
    buildTargets: BuildTargets,
    classes: BuildTargetClasses,
    workspace: () => AbsolutePath,
    buildServer: () => Option[BuildServerConnection]
)(implicit ec: ExecutionContext) {
  private val batch = new BatchedFunction[Request, b.CompileResult](compile)

  private val isCompiling = TrieMap.empty[b.BuildTargetIdentifier, Boolean]
  private var lastCompile: collection.Set[b.BuildTargetIdentifier] = Set.empty

  def currentlyCompiling: Iterable[b.BuildTargetIdentifier] = isCompiling.keys
  def currentlyCompiling(buildTarget: b.BuildTargetIdentifier): Boolean =
    isCompiling.contains(buildTarget)

  def previouslyCompiled: Iterable[b.BuildTargetIdentifier] = lastCompile
  def previouslyCompiled(buildTarget: b.BuildTargetIdentifier): Boolean =
    lastCompile.contains(buildTarget)

  def compileFiles(paths: Seq[AbsolutePath]): Future[b.CompileResult] =
    batch(Request.fromFiles(paths, cascade = false))

  def cascadeCompileFiles(paths: Seq[AbsolutePath]): Future[b.CompileResult] =
    batch(Request.fromFiles(paths, cascade = true))

  def cascadeCompile(target: b.BuildTargetIdentifier): Future[b.CompileResult] =
    batch(Request(List(target), cascade = true))

  def cancel(): Unit = batch.cancelCurrentRequest()

  private def compile(
      requests: Seq[Request]
  ): CancelableFuture[b.CompileResult] = {
    val result = for {
      connection <- buildServer()
      targets = requests.flatMap(_.targets())
      if targets.nonEmpty
    } yield compile(connection, targets)

    result.getOrElse {
      val result = new b.CompileResult(b.StatusCode.CANCELLED)
      Future.successful(result).asCancelable
    }
  }

  private def compile(
      connection: BuildServerConnection,
      targets: Seq[b.BuildTargetIdentifier]
  ): CancelableFuture[b.CompileResult] = {
    val params = new b.CompileParams(targets.asJava)
    targets.foreach(target => isCompiling(target) = true)
    val compilation = connection.compile(params)
    val task = for {
      result <- compilation.asScala
      _ <- {
        lastCompile = isCompiling.keySet
        isCompiling.clear()
        if (result.succeeded) classes.onCompiled(targets)
        else Future.successful(())
      }
    } yield result

    CancelableFuture(
      task,
      Cancelable(() => compilation.cancel(false))
    )
  }

  private trait Request {
    def targets(): Seq[b.BuildTargetIdentifier]
  }

  private object Request {
    private def compilable(path: AbsolutePath): Boolean =
      path.isScalaOrJava && !path.isDependencySource(workspace())

    private def targetsFrom(
        paths: Seq[AbsolutePath]
    ): Seq[b.BuildTargetIdentifier] = {
      val targets =
        paths.filter(compilable).flatMap(buildTargets.inverseSources).distinct

      if (targets.isEmpty) {
        scribe.warn(s"no build target: ${paths.mkString("\n  ")}")
      }
      targets
    }

    def apply(
        targets: Seq[b.BuildTargetIdentifier],
        cascade: Boolean
    ): Request =
      () =>
        if (cascade) targets.flatMap(buildTargets.inverseDependencies).distinct
        else targets

    def fromFiles(paths: Seq[AbsolutePath], cascade: Boolean): Request =
      apply(targetsFrom(paths), cascade)
  }
}
