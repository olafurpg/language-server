package example

object Main {
  for {
    x <- List(1)
    if x > 2
    y <- 1.to(x)
  } yield y
  class Foo[T](name: String, age: T)
  object a {
    new Foo("", 42)
  }
}
