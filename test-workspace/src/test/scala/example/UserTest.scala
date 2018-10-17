package example

import org.scalatest.FunSuite

class UserTest extends FunSuite {
  Main.main() // a
  test("basic") {
    val basic = 42
    assert(basic == 42)
  }
}
