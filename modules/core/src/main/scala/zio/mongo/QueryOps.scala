package zio.mongo

import zio.ZIO
import zio.bson.{BsonDecoder, BsonEncoder}
import zio.stream.ZStream

trait QueryOps {}

trait QueryBuilder[-R] {
  def one[A](implicit ev: BsonDecoder[A]): ZIO[R, Throwable, Option[A]]

  def stream[A](limit: Option[Int] = None, chunkSize: Int = 101)(implicit ev: BsonDecoder[A]): ZStream[R, Throwable, A]

  def explain[A](implicit ev: BsonDecoder[A]): ZIO[R, Throwable, Option[A]]

  def allowDiskUse(allowDiskUse: Boolean): QueryBuilder[R]

  def collation(collation: Collation): QueryBuilder[R]

  def comment(comment: String): QueryBuilder[R]

  def hint(hint: String): QueryBuilder[R]

  def noCursorTimeout(noCursorTimeout: Boolean): QueryBuilder[R]

  def projection[P: BsonEncoder](projection: P): QueryBuilder[R]

  def skip(skip: Int): QueryBuilder[R]

  def sort[S: BsonEncoder](sort: S): QueryBuilder[R]
}

object QueryBuilder {

  private[mongo] case class Impl() extends QueryBuilder[Collection] {}

}
