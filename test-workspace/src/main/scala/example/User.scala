package example

object Main extends Serializable {
  import scala.util.Try
  import scala.util.Failure
  import scala.util.Success
  Try(1) match {
    case Failure(exception) =>
      println(exception)
    case Success(value) =>
  }
  val out = List(1)
  val end = 42
  List(1).map { x =>
    Nil
  }
}
