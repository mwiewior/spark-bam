package org.hammerlab.bam.check.full

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.hammerlab.bam.check
import org.hammerlab.bam.check.full.error.Flags.toCounts
import org.hammerlab.bam.check.full.error.{ Counts, Flags }
import org.hammerlab.bam.check.{ Args, False }
import org.hammerlab.bgzf.Pos
import org.hammerlab.bgzf.block.SeekableByteStream
import org.hammerlab.genomics.reference.NumLoci
import org.hammerlab.math.Monoid.{ mzero ⇒ zero }
import org.hammerlab.math.MonoidSyntax._

import scala.collection.SortedMap

object Run
  extends check.Run[Option[Flags], PosResult] {

  override def makeChecker: (SeekableByteStream, Map[Int, NumLoci]) ⇒ Checker =
    Checker.apply

  override def makeResult(numCalls: Long,
                          results: RDD[(Pos, PosResult)],
                          numFalseCalls: Long,
                          falseCalls: RDD[(Pos, False)]): Result = {
    /**
     * How many times each flag correctly rules out a [[Pos]], grouped by how many total flags ruled out that [[Pos]].
     *
     * Useful for identifying e.g. flags that tend to be "critical" (necessary to avoid [[PosResult]] read-boundary
     * [[Call]]s).
     */
    val trueNegativesByNumNonzeroFields: Array[(Int, Counts)] =
      results
        .flatMap {
          _._2 match {
            case TrueNegative(error) ⇒ Some(toCounts(error))
            case _ ⇒ None
          }
        }
        .keyBy(_.numNonZeroFields)
        .reduceByKey(_ |+| _, 20)  // Total number of distinct keys will be the number of fields in an [[ErrorT]]
        .collect()
        .sortBy(_._1)

    /**
     * CDF to [[trueNegativesByNumNonzeroFields]]'s PDF: how many times does each flag correctly rule out [[Pos]]s that
     * were ruled out by *at most `n`* total flags, for each `n`.
     */
    val trueNegativesByNumNonzeroFieldsCumulative: Array[(Int, Counts)] =
      trueNegativesByNumNonzeroFields
      .scanLeft(
        0 → zero[Counts]
      ) {
        (soFar, next) ⇒
          val (_, countSoFar) = soFar
          val (numNonZeroFields, count) = next
          numNonZeroFields → (countSoFar |+| count)
      }
      .drop(1)  // Discard the dummy/initial "0" entry added above to conform to [[scanLeft]] API

    /**
     * Zip [[trueNegativesByNumNonzeroFields]] and [[trueNegativesByNumNonzeroFieldsCumulative]]: PDF and CDF, keyed by
     * the number of flags ruling out positions.
     */
    val countsByNonZeroFields: Array[(Int, (Counts, Counts))] =
      for {
        ((numNonZeroFields, counts), (_, cumulativeCounts)) ←
        trueNegativesByNumNonzeroFields
        .zip(
          trueNegativesByNumNonzeroFieldsCumulative
        )
      } yield
        numNonZeroFields → (counts, cumulativeCounts)

    /**
     * "Critical" error counts: how many times each flag was the *only* flag identifying a read-boundary-candidate as
     * false.
     */
    val criticalErrorCounts = countsByNonZeroFields.head._2._1

    /**
     * "Total" error counts: how many times each flag ruled out a position, over the entire dataset
     */
    val totalErrorCounts = countsByNonZeroFields.last._2._2

    Result(
      numCalls,
      results,
      numFalseCalls,
      falseCalls,
      criticalErrorCounts,
      totalErrorCounts,
      SortedMap(countsByNonZeroFields: _*)
    )
  }

  override def makePosResult: check.MakePosResult[Option[Flags], PosResult] = MakePosResult

  override def apply(sc: SparkContext, args: Args): check.Result[PosResult] = {
    val result = super.apply(sc, args)

    val Result(
      numCalls,
      _,
      numFalseCalls,
      _,
      criticalErrorCounts,
      totalErrorCounts,
      countsByNonZeroFields
    ) =
      result

    if (!args.eager) {
      println("Critical error counts (true negatives where only one check failed):")
      println(criticalErrorCounts.pp(includeZeros = false))
      println("")

      countsByNonZeroFields
        .get(2)
        .foreach {
          counts ⇒
            println("True negatives where exactly two checks failed:")
            println(
              counts
                ._1
                .pp(includeZeros = false)
            )
            println("")
        }

      println("Total error counts:")
      println(totalErrorCounts.pp())
      println("")
    }

    result
  }
}

