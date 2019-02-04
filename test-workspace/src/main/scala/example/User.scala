package example

// import Java
case class User(
    name: String = "John",
    age: Int = 42,
    address: String = "Street",
    country: String = "is"
)

object User {
  def user(name: String, age: Int) = age
  user("", age = 42)
}
