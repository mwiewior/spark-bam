package org.hammerlab.bam.header

import hammerlab.path._
import htsjdk.samtools.{ SAMFileHeader, SAMSequenceDictionary, SAMSequenceRecord }
import org.hammerlab.bgzf.Pos
import org.hammerlab.bgzf.block.{ UncompressedBytes, UncompressedBytesI }
import org.hammerlab.channel.ByteChannel
import org.hammerlab.genomics.reference.{ ContigName, NumLoci }

import scala.collection.JavaConverters._
import scala.collection.immutable.SortedMap

case class Header(contigLengths: ContigLengths,
                  endPos: Pos,
                  uncompressedSize: Long)

object Header {

  def apply(path: Path)(implicit cf: ContigName.Factory): Header = {
    val uncompressedBytes = UncompressedBytes(path.inputStream)
    val header = apply(uncompressedBytes)
    uncompressedBytes.close()
    header
  }

  def apply(byteStream: UncompressedBytesI[_])(implicit cf: ContigName.Factory): Header = {
    val uncompressedByteChannel: ByteChannel = byteStream
    require(
      uncompressedByteChannel.readString(4, includesNull = false) == "BAM\1"
    )

    val headerLength = uncompressedByteChannel.getInt

    /** Skip [[headerLength]] bytes */
    uncompressedByteChannel.skip(headerLength)

    val numReferences = uncompressedByteChannel.getInt

    val contigLengths =
      SortedMap(
        (for {
          idx ← 0 until numReferences
        } yield {
          val refNameLen = uncompressedByteChannel.getInt
          val refName = uncompressedByteChannel.readString(refNameLen)

          idx →
            (
              refName: ContigName,
              NumLoci(uncompressedByteChannel.getInt)
            )
        }): _*
      )

    Header(
      contigLengths,
      byteStream.curPos.get,
      uncompressedByteChannel.position()
    )
  }

  implicit def toHTSJDKHeader(header: Header): SAMFileHeader =
    new SAMFileHeader(
      new SAMSequenceDictionary(
        (for {
          (name, length) ← header.contigLengths.values.toList
        } yield
          new SAMSequenceRecord(name.name, length.toInt)
        )
        .asJava
      )
    )

  import org.hammerlab.kryo._
  implicit val alsoRegister: AlsoRegister[Header] =
    AlsoRegister(
      cls[ContigLengths],
      cls[Pos]
    )
}
