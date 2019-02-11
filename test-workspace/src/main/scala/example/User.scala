package myexample

object A {
  import myexample.Outer.Inner
  java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(""))
}

object Outer {
  class Inner
}
