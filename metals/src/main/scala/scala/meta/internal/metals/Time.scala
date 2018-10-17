package scala.meta.internal.metals

trait Time {
  def nanos(): Long
}

object Time {
  object system extends Time {
    def nanos(): Long = System.nanoTime()
  }
}
