package scala.meta.internal.metals

import scala.util.{Try => _, _}
import scala.concurrent.{Future => Try}

object Gitignored {
  Try.successful(42)
  Failure(new Exception())
}
