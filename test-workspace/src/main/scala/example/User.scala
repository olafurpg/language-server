package example

import java.util.concurrent.CompletableFuture

object Main extends CompletableFuture[Int] {
  val x = """
  |
  |Hello world
  |println(42)
  |Hello
  |world
  |
  |Hello
  |42
  |
  |object main {
  
    |
    |
    |
    |
    |
    |
    |
    |
  }
  |
  |""".stripMargin
}
