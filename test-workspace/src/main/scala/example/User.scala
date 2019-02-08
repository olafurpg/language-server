package myexample

object A {
  import scala.util.DynamicVariable
  val x = 2
  import myexample.Outer.Inner
}

object Outer {
  class Inner
}
