package example

// import Java
case class User(
    name: String = "John",
    age: Int = 42,
    address: String = "Street",
    country: String = "is"
)

object User {
  def user(name: String, age: Int): Int = age
  def user(name: String, age: Int, street: Int): Int = age
  def bar = user(street = 42, name = "", age = 2)
}
