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

  checkEditLine(
    "conflict",
    s"""package a.b
       |abstract class Conflict {
       |  def self: Conflict
       |}
       |object Main {
       |  class Conflict
       |  new a.b.Conflict {
       |    ___
       |  }
       |}
       |""".stripMargin,
    "def self@@",
    "def self: a.b.Conflict = ${0:???}"
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

  checkEditLine(
    "mutable",
    s"""|abstract class Mutable {
        |  def foo: scala.collection.mutable.Set[Int]
        |}
        |object Main {
        |  new Mutable {
        |___
        |  }
        |}
        |""".stripMargin,
    "    def foo@@",
    """    import scala.collection.mutable
      |    def foo: mutable.Set[Int] = ${0:???}""".stripMargin
  )

  checkEditLine(
    "jutil",
    s"""|abstract class JUtil {
        |  def foo: java.util.List[Int]
        |}
        |class Main extends JUtil {
        |___
        |}
        |""".stripMargin,
    "  def foo@@",
    """  import java.{util => ju}
      |  def foo: ju.List[Int] = ${0:???}""".stripMargin
  )

  checkEditLine(
    "jlang",
    s"""|abstract class Mutable {
        |  def foo: java.lang.StringBuilder
        |}
        |class Main extends Mutable {
        |  ___
        |}
        |""".stripMargin,
    "  def foo@@",
    """  def foo: java.lang.StringBuilder = ${0:???}""".stripMargin
  )

  checkEditLine(
    "alias",
    s"""|
        |abstract class Abstract {
        |  def foo: scala.collection.immutable.List[Int]
        |}
        |class Main extends Abstract {
        |  ___
        |}
        |
        |""".stripMargin,
    "  def foo@@",
    """  def foo: List[Int] = ${0:???}""".stripMargin
  )

  checkEditLine(
    "alias2",
    s"""|
        |abstract class Abstract {
        |  type Foobar = List[Int]
        |  def foo: Foobar
        |}
        |class Main extends Abstract {
        |  ___
        |}
        |
        |""".stripMargin,
    "  def foo@@",
    // NOTE(olafur) I am not sure this is desirable behavior, we might want to
    // consider not dealiasing here.
    """  def foo: List[Int] = ${0:???}""".stripMargin
  )

  checkEditLine(
    "rename",
    s"""|import java.lang.{Boolean => JBoolean}
        |abstract class Abstract {
        |  def foo: JBoolean
        |}
        |class Main extends Abstract {
        |___
        |}
        |
        |""".stripMargin,
    "  def foo@@",
    // NOTE(olafur) I am not sure this is desirable behavior, we might want to
    // try and detect that we should use m here.
    """  def foo: JBoolean = ${0:???}""".stripMargin
  )
}
