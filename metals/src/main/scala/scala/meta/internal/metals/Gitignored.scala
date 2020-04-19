package scala.meta.internal.metals

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future => Try}
import scala.util._
import scala.util.{Try => _}

object Gitignored {
  Try(42)
  Failure(new Exception())
}
