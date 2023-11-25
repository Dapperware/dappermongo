package zio.mongo

import zio.bson.BsonDecoder
import zio.{Chunk, Duration, ZIO}

trait DistinctOps {

  def distinct(field: String): DistinctBuilder[Collection]

}

trait DistinctBuilder[-R] {
  def filter[T: BsonDecoder](filter: T): DistinctBuilder[R]

  def maxTime(maxTime: Duration): DistinctBuilder[R]

  def collation(collation: Collation): DistinctBuilder[R]

  def batchSize(batchSize: Int): DistinctBuilder[R]

  def comment(comment: String): DistinctBuilder[R]

  def toSet[T: BsonDecoder]: ZIO[R, Throwable, Set[T]]

  def toChunk[T: BsonDecoder]: ZIO[R, Throwable, Chunk[T]]
}
