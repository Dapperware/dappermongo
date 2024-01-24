package dappermongo

import reactivemongo.api.bson.{BSONDocumentReader, BSONDocumentWriter, BSONWriter}
import zio.stream.ZStream
import zio.{Duration, ZIO}

trait AggregateOps {

  def aggregate: AggregateBuilder[Collection]

}

trait AggregateBuilder[-R] {

  def allowDiskUse(allowDiskUse: Boolean): AggregateBuilder[R]

  def batchSize(batchSize: Int): AggregateBuilder[R]

  def bypassDocumentValidation(bypassDocumentValidation: Boolean): AggregateBuilder[R]

  def collation(collation: Option[Collation]): AggregateBuilder[R]

  def comment[T: BSONWriter](comment: T): AggregateBuilder[R]

  def hint[T: BSONDocumentWriter](hint: T): AggregateBuilder[R]

  def maxTime(maxTime: Duration): AggregateBuilder[R]

  def maxAwaitTime(maxAwaitTime: Duration): AggregateBuilder[R]

  /**
   * Runs the aggregation pipeline into another collection.
   */
  def out(collection: Collection): ZIO[R, Throwable, Unit]

  def one[T: BSONDocumentReader]: ZIO[R, Throwable, Option[T]]

  def stream[T: BSONDocumentReader]: ZStream[Any, Throwable, T]
}
