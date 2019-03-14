package example

import java.lang.{Boolean => JBoolean}

abstract class Abstract {
  def foo: java.lang.Boolean
}

class Main extends Abstract {
  def foo: JBoolean = ???
}
