package scala.meta.internal.pc

case class FlatFile(
    path: String,
    offset: Long,
    compressedSize: Int,
    origSize: Int
)
