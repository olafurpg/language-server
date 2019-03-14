package tests.pc

import tests.BaseCompletionSuite

object CompletionOverrideSuite extends BaseCompletionSuite {

  override def beforeAll(): Unit = {
    indexJDK()
  }

  checkEdit(
    "basic",
    """
      |object Main extends AutoCloseable {
      |  def close@@
      |}
    """.stripMargin,
    """
      |object Main extends AutoCloseable {
      |  def close(): Unit = ${0:???}
      |}
      |""".stripMargin
  )

  checkEdit(
    "overload",
    """
      |trait Interface {
      |  def foo(a: Int): Unit
      |  def foo(a: String): Unit
      |}
      |object Main extends Interface {
      |  override def foo(a: Int): Unit = ()
      |  override def foo@@
      |}
    """.stripMargin,
    """
      |trait Interface {
      |  def foo(a: Int): Unit
      |  def foo(a: String): Unit
      |}
      |object Main extends Interface {
      |  override def foo(a: Int): Unit = ()
      |  override def foo(a: String): Unit = ${0:???}
      |}
      |""".stripMargin
  )

  checkEdit(
    "seen-from",
    """
      |object Main {
      |  new Iterable[Int] {
      |    def iterato@@
      |  }
      |}
    """.stripMargin,
    """
      |object Main {
      |  new Iterable[Int] {
      |    def iterator: Iterator[Int] = ${0:???}
      |  }
      |}
      |""".stripMargin
  )

  checkEdit(
    "generic",
    """
      |object Main {
      |  new scala.Traversable[Int] {
      |    def foreach@@
      |  }
      |}
    """.stripMargin,
    """
      |object Main {
      |  new scala.Traversable[Int] {
      |    def foreach[U](f: Int => U): Unit = ${0:???}
      |  }
      |}
      |""".stripMargin
  )

  checkEdit(
    "context-bound",
    """
      |trait Context {
      |   def add[T:Ordering]: T
      |}
      |object Main {
      |  new Context {
      |    override def ad@@
      |  }
      |}
    """.stripMargin,
    """
      |trait Context {
      |   def add[T:Ordering]: T
      |}
      |object Main {
      |  new Context {
      |    override def add[T: Ordering]: T = ${0:???}
      |  }
      |}
      |""".stripMargin
  )

  checkEdit(
    "import",
    """
      |object Main {
      |  new java.nio.file.SimpleFileVisitor[java.nio.file.Path] {
      |    def visitFil@@
      |  }
      |}
    """.stripMargin,
    """
      |object Main {
      |  new java.nio.file.SimpleFileVisitor[java.nio.file.Path] {
      |    import java.nio.file.{FileVisitResult, Path}
      |    import java.nio.file.attribute.BasicFileAttributes
      |    override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = ${0:???}
      |  }
      |}
      |""".stripMargin,
    assertSingleItem = false
  )

  check(
    "empty",
    """
      |trait SuperAbstract {
      |  def aaa: Int = 2
      |}
      |trait Abstract extends SuperAbstract {
      |  def bbb: Int = 2
      |  type TypeAlias = String // should be ignored
      |}
      |object Main {
      |  new Abstract {
      |    def @@
      |  }
      |}
    """.stripMargin,
    // assert that `isInstanceOf` and friends are not included
    """|override def aaa: Int: Int
       |override def bbb: Int: Int
       |override def equals(obj: Any): Boolean(obj: Any): Boolean
       |override def hashCode(): Int(): Int
       |override def toString(): String(): String
       |override def clone(): Object(): Object
       |override def finalize(): Unit(): Unit
       |""".stripMargin
  )

  def implement(completion: String): String =
    s"""
       |trait Abstract {
       |  def implementMe: Int
       |}
       |object Main {
       |  new Abstract {
       |    $completion
       |  }
       |}
    """.stripMargin
  checkEdit(
    "implement",
    // assert that `override` is not inserted.
    implement("def implement@@"),
    implement("def implementMe: Int = ${0:???}")
  )
  checkEdit(
    "implement-override",
    // assert that `override` is inserted.
    implement("override def implement@@"),
    implement("override def implementMe: Int = ${0:???}")
  )

  checkEdit(
    "error",
    """
      |object Main {
      |  new scala.Iterable[Unknown] {
      |    def iterato@@
      |  }
      |}
    """.stripMargin,
    // Replace error types with `Any`, mirroring the IntelliJ behavior.
    """
      |object Main {
      |  new scala.Iterable[Unknown] {
      |    def iterator: Iterator[Any] = ${0:???}
      |  }
      |}
  """.stripMargin
  )

  check(
    "sort",
    """
      |trait Super {
      |  def a: Int = 2
      |  def b: Int
      |}
      |object Main {
      |  new Super {
      |    def @@
      |  }
      |}
    """.stripMargin,
    // assert that `isInstanceOf` and friends are not included
    """|def b: Int: Int
       |override def a: Int: Int
       |""".stripMargin,
    topLines = Some(2)
  )

  def conflict(query: String): String =
    s"""package a.b
       |abstract class Conflict {
       |  def self: Conflict
       |}
       |object Main {
       |  class Conflict
       |  new a.b.Conflict {
       |    $query
       |  }
       |}
       |""".stripMargin
  checkEdit(
    "conflict",
    conflict("def self@@"),
    conflict("def self: a.b.Conflict = ${0:???}")
  )

  check(
    "conflict2",
    s"""package a.b
       |abstract class Conflict {
       |  type Inner
       |  def self: Conflict
       |  def selfArg: Option[Conflict]
       |  def selfPath: Conflict#Inner
       |}
       |object Main {
       |  class Conflict
       |  val a = 2
       |  new _root_.a.b.Conflict {
       |    def self@@
       |  }
       |}
       |""".stripMargin,
    """|def self: _root_.a.b.Conflict: Conflict
       |def selfArg: Option[_root_.a.b.Conflict]: Option[Conflict]
       |def selfPath: _root_.a.b.Conflict#Inner: Conflict#Inner
       |""".stripMargin
  )
}
