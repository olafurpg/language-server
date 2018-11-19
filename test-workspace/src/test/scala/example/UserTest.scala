package example

import org.scalatest.FunSuite
import cats._
import cats.implicits._

class UserTest extends FunSuite {
  test("basic") {
    val basic = Main.number
    assert(basic == 42)
  }
}
