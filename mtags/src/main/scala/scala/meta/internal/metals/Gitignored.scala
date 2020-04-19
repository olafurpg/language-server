package scala.meta.internal.metals

import scala.meta.internal.metals.HasTry._
import scala.util._
import scala.util.{Try => _}

object HasTry {
  object Try {
    def successful(n: Int): Int = n
  }
}

object Gitignored {
  Try.successful(42)
  Failure(new Exception())
}
