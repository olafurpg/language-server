package scala.meta.internal.metals
import java.util.concurrent.TimeUnit

class Timer(time: Time) {
  val startNanos: Long = time.nanos()
  def elapsedNanos: Long = {
    val now = time.nanos()
    now - startNanos
  }
  def elapsedMillis: Long = {
    TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
  }
  def elapsedSeconds: Long = {
    TimeUnit.NANOSECONDS.toSeconds(elapsedNanos)
  }
  override def toString: String = {
    Timer.readableNanos(elapsedNanos)
  }
}

object Timer {
  def readableNanos(n: Long): String = {
    val seconds = TimeUnit.NANOSECONDS.toSeconds(n)
    if (seconds > 0) readableSeconds(seconds)
    else {
      val ms = TimeUnit.NANOSECONDS.toMillis(n)
      s"${ms}ms"
    }
  }
  def readableSeconds(n: Long): String = {
    val minutes = n / 60
    val seconds = n % 60
    if (minutes > 0) {
      if (seconds == 0) s"${minutes}m"
      else s"${minutes}m${seconds}s"
    } else {
      s"${seconds}s"
    }
  }
}
