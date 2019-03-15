package example

import java.lang.{Boolean => JBoolean}

abstract class Path {
  type Out
  def out: Out
}
class Main extends Path {
  def out: Out = ???
  trait Conflict {
    def conflict: Out
  }
  object Conflict extends Conflict {
    type Out = Int
    def conflict: Main.this.Out = ???
  }
}
