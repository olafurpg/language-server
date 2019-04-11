package scala.meta.internal.metals

import java.{util => ju}
import java.nio.file.Path
import scala.meta.pc.CancelToken
import scala.meta.pc.OffsetParams

case class CompilerOffsetParams(
    filename: String,
    text: String,
    offset: Int,
    token: CancelToken = EmptyCancelToken,
    path: ju.Optional[Path] = ju.Optional.empty(),
    sourceDirectory: ju.Optional[Path] = ju.Optional.empty(),
) extends OffsetParams
