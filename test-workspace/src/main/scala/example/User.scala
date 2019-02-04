package example

// import Java

object User {
  class Foo[A] {
    def add[B <: A](a: A, e: B): Nothing = ???
  }
  Map.empty[Int, String].applyOrElse(1, (a: Int) => "")
}
