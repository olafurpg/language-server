package example

class Foo {
  def foo: Option[Foo] = Some(this)
}

object Main {
  class Foo
  val example = 42
  new _root_.example.Foo {}
}
