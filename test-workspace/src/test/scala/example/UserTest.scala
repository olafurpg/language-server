package example

import org.scalatest.FunSuite
import cats._
import cats.implicits._

class UserTest extends FunSuite {
  Some(1)
  test("basic") {
    val basic = Main.number
    assert(basic == 42)
  }
}
