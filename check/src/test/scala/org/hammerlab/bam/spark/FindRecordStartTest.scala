package org.hammerlab.bam.spark

import org.hammerlab.bam.check.Checker.{ MaxReadSize, ReadsToCheck, default }
import org.hammerlab.bam.header.ContigLengths
import org.hammerlab.bam.test.resources.bam1
import org.hammerlab.bgzf.Pos
import org.hammerlab.bgzf.block.SeekableUncompressedBytes
import org.hammerlab.channel.SeekableByteChannel
import org.hammerlab.hadoop.Configuration
import org.hammerlab.test.Suite

class FindRecordStartTest
  extends Suite {

  test("470kb") {
    val path = bam1
    val ch = SeekableByteChannel(path)
    implicit val uncompressedBytes = SeekableUncompressedBytes(ch)
    implicit val conf = Configuration()
    implicit val contigLengths = ContigLengths(path)
    implicit val maxReadSize = default[MaxReadSize]
    implicit val readsToCheck = default[ReadsToCheck]

    FindRecordStart(path, 486847) should be(Pos(486847, 7))
  }
}
