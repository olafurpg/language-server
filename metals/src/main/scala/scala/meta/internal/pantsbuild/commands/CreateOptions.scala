package scala.meta.internal.pantsbuild

import metaconfig.generic
import metaconfig.annotation._
import metaconfig.generic.Settings
import metaconfig.{ConfDecoder, ConfEncoder}

case class CreateOptions(
    name: String = "",
    @ExtraName("remainingArgs")
    targets: List[String] = Nil,
    @Inline common: SharedOptions = SharedOptions.default
)

object CreateOptions {
  val default: CreateOptions = CreateOptions()
  implicit lazy val surface: generic.Surface[CreateOptions] =
    generic.deriveSurface[CreateOptions]
  implicit lazy val encoder: ConfEncoder[CreateOptions] =
    generic.deriveEncoder[CreateOptions]
  implicit lazy val decoder: ConfDecoder[CreateOptions] =
    generic.deriveDecoder[CreateOptions](default)
  implicit lazy val settings: Settings[CreateOptions] = Settings[CreateOptions]
}
