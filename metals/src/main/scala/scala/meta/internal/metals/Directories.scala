package scala.meta.internal.metals
import scala.meta.io.RelativePath

object Directories {
  def readonly: RelativePath =
    RelativePath(".metals").resolve("readonly")
}
