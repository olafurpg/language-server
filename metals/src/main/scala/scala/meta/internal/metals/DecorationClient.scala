package scala.meta.internal.metals

import javax.annotation.Nullable
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.MarkedString

trait DecorationClient {
  @JsonNotification("metals/decorationTypeDidChange")
  def metalsDecorationTypeDidChange(
      params: DecorationTypeDidChange
  ): Unit
  @JsonNotification("metals/decorationRangesDidChange")
  def metalsDecorationRangesDidChange(
      params: DecorationRangesTypeDidChange
  ): Unit
}

object DecorationRangeBehavior {
  val OpenOpen = 0
  val ClosedClosed = 1
  val OpenClosed = 2
  val ClosedOpen = 3
}

object OverviewRulerLane {
  val Left = 1
  val Center = 2
  val Right = 4
  val Full = 7
}

case class DecorationTypeDidChange(
    @Nullable wholeLine: java.lang.Boolean = null,
    @Nullable rangeBehavior: java.lang.Integer = null,
    @Nullable overviewRulerLane: java.lang.Integer = null
)


case class ThemableDecorationAttachmentRenderOptions(
  @Nullable contextText: String = null,
)

case class DecorationOptions(
  range: Range,
  @Nullable hoverMessage: MarkedString = null,
  @Nullable renderOptions: DecorationInstanceRenderOptions = null
)

case class DecorationRangesTypeDidChange(
     uri: String,
     options: 
    @Nullable rangeBehavior: java.lang.Integer = null,
    @Nullable overviewRulerLane: java.lang.Integer = null
)
