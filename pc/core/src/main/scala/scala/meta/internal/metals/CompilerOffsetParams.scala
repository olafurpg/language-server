package scala.meta.internal.metals

import org.eclipse.lsp4j.jsonrpc.CancelChecker
import scala.meta.pc.OffsetParams

case class CompilerOffsetParams(
    filename: String,
    text: String,
    offset: Int,
    token: CancelChecker = EmptyCancelChecker
) extends OffsetParams
