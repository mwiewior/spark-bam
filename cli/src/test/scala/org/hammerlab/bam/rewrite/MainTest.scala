package org.hammerlab.bam.rewrite

import caseapp.RemainingArgs
import org.hammerlab.args.{ Endpoints, IntRanges }
import org.hammerlab.bam.test.resources.bam2
import org.hammerlab.test.Suite
import org.hammerlab.test.matchers.files.DirMatcher.dirMatch
import org.hammerlab.test.resources.File

class MainTest
  extends Suite {

  /**
   * Use [[Main]] to pull records [100,1000) out of 2.bam, test that the results are as expected.
   */
  test("slice 2.bam") {
    val outDir = tmpDir()
    Main.run(
      Args(
        readRanges =
          Some(
            IntRanges(
              Seq(
                Endpoints(100, 1000)
              )
            )
          ),
        indexBlocks = true,
        indexRecords = true
      ),
      RemainingArgs(
        Seq(
          bam2.toString,
          s"$outDir/2.100-1000.bam"
        ),
        Nil
      )
    )

    outDir should dirMatch(File("slice"))
  }
}
