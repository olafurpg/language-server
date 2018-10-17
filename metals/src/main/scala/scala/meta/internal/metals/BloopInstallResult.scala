package scala.meta.internal.metals

sealed abstract class BloopInstallResult extends Product with Serializable {
  import BloopInstallResult._
  def isInstalled: Boolean = this == Installed
  def isFailed: Boolean = this.isInstanceOf[Failed]
}
object BloopInstallResult {
  case object Rejected extends BloopInstallResult
  case object Unchanged extends BloopInstallResult
  case object Installed extends BloopInstallResult
  case object Cancelled extends BloopInstallResult
  case class Failed(exit: Int) extends BloopInstallResult
}
