package tests

object RenameSlowSuite extends BaseSlowSuite("rename") {
  testAsync("case-class") {
    cleanWorkspace()
    for {
      _ <- server.initialize(
        """
          |/metals.json
          |{
          |  "a": {},
          |  "b": {"dependsOn": ["a"]},
          |  "c": {"dependsOn": ["a"]}
          |}
          |/a/src/main/scala/a/Animal.scala
          |package a
          |trait Animal {
          |  def sound(): Unit
          |}
          |/b/src/main/scala/b/Cat.scala
          |package b
          |class Cat  extends a.Animal {
          |  override def sound() = println("meow")
          |}
          |/c/src/main/scala/c/Dog.scala
          |package c
          |class Dog extends a.Animal {
          |  def puppy(): a.Animal = new a.Animal {
          |    override def sound(): Unit = println("waf")
          |  }
          |  override def sound() = println("woof")
          |}
          |""".stripMargin,
        preInitialized = { () =>
          server.didOpen("a/src/main/scala/a/Animal.scala")
        }
      )
      _ = assertNoDiagnostics()
      _ <- server.didOpen("a/src/main/scala/a/Animal.scala")
      _ <- server.rename("c/src/main/scala/c/Dog.scala", "so@@und", "makeSound")
    } yield ()
  }
}
