package zio.zmx.state

import zio.zmx.internal.ScalaCompat._

import zio.Chunk

final case class DoubleHistogramBuckets(buckets: Chunk[(Double, Long)]) {
  def boundaries: Chunk[Double] =
    buckets.map(_._1)
}

object DoubleHistogramBuckets {

  def manual(limits: Double*): DoubleHistogramBuckets = {
    val boundaries = Chunk.fromArray(limits.toArray.sorted(dblOrdering)) ++ Chunk(Double.MaxValue).distinct
    DoubleHistogramBuckets(boundaries.map(boundary => (boundary, 0L)))
  }

  def linear(start: Double, width: Double, count: Int): DoubleHistogramBuckets = {
    val boundaries = Chunk.fromArray(0.until(count).map(i => start + i * width).toArray) ++ Chunk(Double.MaxValue)
    DoubleHistogramBuckets(boundaries.map(boundary => (boundary, 0L)))
  }

  def exponential(start: Double, factor: Double, count: Int): DoubleHistogramBuckets = {
    val boundaries =
      Chunk.fromArray(0.until(count).map(i => start * Math.pow(factor, i.toDouble)).toArray) ++ Chunk(Double.MaxValue)
    DoubleHistogramBuckets(boundaries.map(boundary => (boundary, 0L)))
  }
}
