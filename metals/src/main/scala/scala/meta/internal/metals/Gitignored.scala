package scala.meta.internal.metals

import scala.concurrent.{Future => Try}
import scala.util.{Try => _, _}

object Gitignored {
  Try.successful(42)
  Failure(new Exception())
}
