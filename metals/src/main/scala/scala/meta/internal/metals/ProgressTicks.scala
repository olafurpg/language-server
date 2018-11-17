package scala.meta.internal.metals

abstract class ProgressTicks {
  def format(i: Int): String
}
object ProgressTicks {
  object Braille extends ProgressTicks {
    val value = "⠇⠋⠙⠸⠦⠴"
    override def format(i: Int): String = {
      value.charAt(i % value.length).toString
    }
  }
  object Dots extends ProgressTicks {
    val value = Array(
      "   ",
      ".  ",
      ".. ",
      "..."
    )
    override def format(i: Int): String = {
      value(i % value.length)
    }
  }
}
