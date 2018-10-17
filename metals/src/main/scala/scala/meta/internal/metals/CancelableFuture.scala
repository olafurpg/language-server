package scala.meta.internal.metals

import scala.concurrent.CanAwait
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

final case class CancelableFuture[T](cancelable: Cancelable, future: Future[T])
    extends Future[T]
    with Cancelable {

  override def onComplete[U](
      f: Try[T] => U
  )(implicit executor: ExecutionContext): Unit = {
    future.onComplete(f)
  }

  override def isCompleted: Boolean = {
    future.isCompleted
  }

  override def value: Option[Try[T]] = {
    future.value
  }

  override def transform[S](
      f: Try[T] => Try[S]
  )(implicit executor: ExecutionContext): Future[S] = {
    future.transform(f)
  }

  override def transformWith[S](
      f: Try[T] => Future[S]
  )(implicit executor: ExecutionContext): Future[S] = {
    future.transformWith(f)
  }

  override def cancel(): Unit = {
    cancelable.cancel()
  }

  override def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
    future.ready(atMost)
    this
  }

  override def result(atMost: Duration)(implicit permit: CanAwait): T =
    future.result(atMost)

}
