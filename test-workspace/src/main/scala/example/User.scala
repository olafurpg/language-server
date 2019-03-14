package example

abstract class Abstract {
  def foo: java.lang.StringBuilder
}
class Main extends Abstract {
  val java = 1
  def foo: _root_.java.lang.StringBuilder = ???
}
