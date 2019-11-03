package scala.meta.internal.decorations

import javax.annotation.Nullable

case class ThemableDecorationAttachmentRenderOptions(
    @Nullable contextText: String = null,
    @Nullable contentIconPath: String = null,
    @Nullable border: String = null,
    @Nullable fontStyle: String = null,
    @Nullable fontWeight: String = null,
    @Nullable textDecoration: String = null,
    @Nullable color: String = null,
    @Nullable backgroundColor: String = null,
    @Nullable margin: String = null,
    @Nullable width: String = null,
    @Nullable height: String = null,
    @Nullable light: ThemableDecorationAttachmentRenderOptions = null,
    @Nullable dark: ThemableDecorationAttachmentRenderOptions = null
)
