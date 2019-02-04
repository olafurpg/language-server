package example

// import Java

object User {
  case class User(
      name: String = "John",
      age: Int = 42,
      address: String = "Street",
      country: String = "is"
  )
  User(age = 42)
  // Map.empty[Int, String].applyOrElse[]

  def user(name: String, age: Int): Int = age
  def user(name: String, age: Int, street: Int): Int = age
  // user(age = 1, street)
}
