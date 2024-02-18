package dappermongo.aggregate
import scala.util.{Failure, Success, Try}

import reactivemongo.api.bson.{BSON, BSONDocument}
import zio.{Chunk, NonEmptyChunk}

case class Pipeline(stages: NonEmptyChunk[Stage]) {
  def >>>[T <: Stage](that: T)(implicit ev: CanFollow[T]): Pipeline = {
    implicit val _ = ev // To satisfy scalafix
    copy(stages = stages :+ that)
  }

  def toList: Try[List[BSONDocument]] = {
    val builder          = List.newBuilder[BSONDocument]
    var error: Throwable = null

    @scala.annotation.tailrec
    def loop(head: Stage, rest: Chunk[Stage]): Unit =
      BSON.writeDocument(head) match {
        case Failure(exception) => error = exception
        case Success(value) =>
          builder += value
          rest.headOption match {
            case Some(next) => loop(next, rest.drop(1))
            case None       => // Done
          }
      }

    loop(stages.head, stages.drop(1))

    if (error ne null) Failure(error)
    else Success(builder.result())
  }
}
