package dappermongo.aggregate

import zio.Chunk

case class Pipeline(first: Stage, rest: Chunk[Stage]) {
  def ++[T <: Stage](that: T)(implicit ev: Combiner[T]): Pipeline = copy(rest = rest :+ that)
}
