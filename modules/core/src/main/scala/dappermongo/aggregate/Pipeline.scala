package dappermongo.aggregate

import zio.NonEmptyChunk

case class Pipeline(stages: NonEmptyChunk[Stage]) {
  def >>>[T <: Stage](that: T)(implicit ev: CanFollow[T]): Pipeline = {
    implicit val _ = ev // To satisfy scalafix
    copy(stages = stages :+ that)
  }

}
