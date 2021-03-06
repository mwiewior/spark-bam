package org.hammerlab.bam.iterator

import java.io.{ Closeable, InputStream }

import hammerlab.iterator.SimpleIterator
import org.hammerlab.bam.header.Header
import org.hammerlab.bgzf.Pos
import org.hammerlab.bgzf.block.{ Block, UncompressedBytesI }
import org.hammerlab.channel.ByteChannel

/**
 * Interface for iterators that wrap a (compressed) BAM-file [[InputStream]] and emit one object for each underlying
 * record.
 */
trait RecordIterator[T, UncompressedBytes <: UncompressedBytesI[_]]
  extends SimpleIterator[T]
    with Closeable {

  // Uncompressed bytes; also exposes pointer to current-block
  val uncompressedBytes: UncompressedBytes

  // Uncompressed byte-channel, for reading ints into a buffer
  val uncompressedByteChannel: ByteChannel = uncompressedBytes

  def header: Header

  def curBlock: Option[Block] = uncompressedBytes.curBlock
  def curPos: Option[Pos] = uncompressedBytes.curPos

  override def close(): Unit =
    uncompressedByteChannel.close()
}
