package org.hammerlab.bgzf.block

import java.io.{ EOFException, IOException, InputStream }
import java.util.zip.Inflater

import org.hammerlab.bgzf.block.Block.{ FOOTER_SIZE, MAX_BLOCK_SIZE }
import org.hammerlab.io.{ Buffer, ByteChannel, SeekableByteChannel }
import org.hammerlab.iterator.SimpleBufferedIterator

import scala.collection.mutable

/**
 * Iterator over BGZF [[Block]]s pointed to by a BGZF-compressed [[InputStream]]
 */
trait StreamI
  extends SimpleBufferedIterator[Block] {

  def ch: ByteChannel

  // Buffer for input, bgzf-compressed block data
  private implicit val encBuf = Buffer(MAX_BLOCK_SIZE)

  // Buffer for output, bgzf-decompressed data
  private val decBuf = Array.fill[Byte](MAX_BLOCK_SIZE)(0)

  // Start position of next block to be emitted
  def pos = head.start

  override protected def _advance: Option[Block] = {

    try {

      val start = ch.position()

      encBuf.clear()
      val Header(actualHeaderSize, compressedSize) = Header(ch)

      val remainingBytes = compressedSize - actualHeaderSize

      val dataLength = remainingBytes - FOOTER_SIZE

      ch.read(encBuf, actualHeaderSize, remainingBytes)

      val uncompressedSize = encBuf.getInt(compressedSize - 4)

      val inflater = new Inflater(true)
      inflater.setInput(encBuf.array(), actualHeaderSize, dataLength)
      val bytesDecompressed = inflater.inflate(decBuf, 0, uncompressedSize)
      if (bytesDecompressed != uncompressedSize) {
        throw new IOException(s"Expected $uncompressedSize decompressed bytes, found $bytesDecompressed")
      }

      if (dataLength == 2)
        // Empty block at end of file
        None
      else
        Some(
          Block(
            decBuf.slice(0, uncompressedSize),
            start,
            compressedSize
          )
        )
    } catch {
      case e: EOFException ⇒
        None
    }
  }

  override def close(): Unit = {
    super.close()
    ch.close()
  }
}

case class Stream(ch: ByteChannel)
  extends StreamI

case class SeekableStream(ch: SeekableByteChannel)
  extends StreamI {

  val maxCacheSize = 100

  import scala.collection.JavaConverters._

  val cache: mutable.Map[Long, Block] =
    new java.util.LinkedHashMap[Long, Block]() {
      override def removeEldestEntry(eldest: java.util.Map.Entry[Long, Block]) =
        size > maxCacheSize
    }
    .asScala

  override protected def _advance: Option[Block] = {
    val start = ch.position()
    cache
      .get(start)
      .map {
        block ⇒
          ch.seek(start + block.compressedSize)
          block.idx = 0
          block
      }
      .orElse {
        super._advance.map {
          block ⇒
            cache(start) = block
            block
        }
      }
  }

  def seek(newPos: Long): Boolean = {
    if (!hasNext || pos != newPos) {
      clear()
      ch.seek(newPos)
      true
    } else {
      false
    }
  }
}
