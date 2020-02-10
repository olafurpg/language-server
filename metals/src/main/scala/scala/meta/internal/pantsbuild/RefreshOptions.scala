package scala.meta.internal.pantsbuild

import metaconfig.generic
import metaconfig.annotation._
import metaconfig.generic.Settings
import metaconfig.{ConfDecoder, ConfEncoder}

case class RefreshOptions(
    @ExtraName("remainingArgs")
    names: List[String] = Nil,
    @Hidden()
    update: Boolean = false,
    @Inline common: SharedOptions = SharedOptions()
)
object RefreshOptions {
  val default: RefreshOptions = RefreshOptions()
  implicit lazy val surface: generic.Surface[RefreshOptions] =
    generic.deriveSurface[RefreshOptions]
  implicit lazy val encoder: ConfEncoder[RefreshOptions] =
    generic.deriveEncoder[RefreshOptions]
  implicit lazy val decoder: ConfDecoder[RefreshOptions] =
    generic.deriveDecoder[RefreshOptions](default)
  implicit lazy val settings: Settings[RefreshOptions] =
    Settings[RefreshOptions]
}
