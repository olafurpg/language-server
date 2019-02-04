package example

// import Java

object User {
  val x = Map.empty[Int, String]
  x.collect {
    case (a, b) =>
      b.length
  }
}
