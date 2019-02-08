package scala.meta.internal.metals

import com.google.common.hash.BloomFilter

case class CompressedSourceIndex(
    bloom: BloomFilter[CharSequence],
    symbols: Seq[CachedSymbolInformation]
)
