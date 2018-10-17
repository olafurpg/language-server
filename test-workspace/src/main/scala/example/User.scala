package example

import java.util.concurrent.Future
import java.util._

object Main {
  def main(): Unit = {
    val x = sourcecode.Line.generate
    println(x.value)
    Collections.singletonList(x)
    println("Hello world!")
  }
}
