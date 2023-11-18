package zio.mongo

import com.mongodb.reactivestreams.client.MongoDatabase
import zio.ZIO
import zio.bson.{BsonDecoder, BsonEncoder}
import zio.stream.ZStream

import zio.interop.reactivestreams.publisherToStream
import zio.mongo.internal.PublisherOps

trait QueryOps {

  def find[Q: BsonEncoder](q: Q): QueryBuilder[Collection]

  def find[Q: BsonEncoder, P: BsonEncoder](q: Q, p: P): QueryBuilder[Collection]

  def findAll: QueryBuilder[Collection]

}

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

  private[mongo] case class Impl(database: MongoDatabase, options: QueryBuilderOptions)
      extends QueryBuilder[Collection] {
    override def one[A](implicit ev: BsonDecoder[A]): ZIO[Collection, Throwable, Option[A]] =
      ZIO.serviceWithZIO { collection =>
        makePublisher(collection, options, Some(1), 1)
          .first()
          .single
          .map {
            case Some(doc) => BsonDecoder[A].fromBsonValue(doc.toBsonDocument()).map(Some(_))
            case None      => Right(None)
          }
          .absolve
      }

    override def stream[A](limit: Option[Int], chunkSize: Int)(implicit
      ev: BsonDecoder[A]
    ): ZStream[Collection, Throwable, A] =
      (for {
        collection <- ZStream.service[Collection]
        doc        <- makePublisher(collection, options, limit, chunkSize).toZIOStream(chunkSize)
      } yield BsonDecoder[A].fromBsonValue(doc.toBsonDocument())).absolve

    override def explain[A](implicit ev: BsonDecoder[A]): ZIO[Collection, Throwable, Option[A]] =
      copy(options = options.copy(explain = Some(true))).one[A]

    override def allowDiskUse(allowDiskUse: Boolean): QueryBuilder[Collection] =
      copy(options = options.copy(allowDiskUse = Some(allowDiskUse)))

    override def collation(collation: Collation): QueryBuilder[Collection] =
      copy(options = options.copy(collation = Some(collation)))

    override def comment(comment: String): QueryBuilder[Collection] =
      copy(options = options.copy(comment = Some(comment)))

    override def hint(hint: String): QueryBuilder[Collection] =
      copy(options = options.copy(hint = Some(hint)))

    override def noCursorTimeout(noCursorTimeout: Boolean): QueryBuilder[Collection] =
      copy(options = options.copy(noCursorTimeout = Some(noCursorTimeout)))

    override def projection[P: BsonEncoder](projection: P): QueryBuilder[Collection] =
      copy(options =
        options.copy(projection = Some(BsonEncoder[P].toBsonValue(projection).asDocument()))
      ) // TODO This conversion is unsafe

    override def skip(skip: Int): QueryBuilder[Collection] =
      copy(options = options.copy(skip = Some(skip)))

    override def sort[S: BsonEncoder](sort: S): QueryBuilder[Collection] =
      copy(options =
        options.copy(sort = Some(BsonEncoder[S].toBsonValue(sort).asDocument()))
      ) // TODO This conversion is unsafe

    private def makePublisher(
      collection: Collection,
      options: QueryBuilderOptions,
      limit: Option[Int],
      batchSize: Int
    ) = {
      val underlying = database.getCollection(collection.name)
      val builder    = options.filter.fold(underlying.find())(filter => underlying.find(filter))

      builder
        .sort(options.sort.orNull)
        .projection(options.projection.orNull)
        .filter(options.filter.orNull)
        .collation(options.collation.map(_.asJava).orNull)
        .comment(options.comment.orNull)
        .hintString(options.hint.orNull)
        .batchSize(batchSize)
        .allowDiskUse(options.allowDiskUse.map(java.lang.Boolean.valueOf).orNull)

      options.skip.foreach(builder.skip)
      limit.foreach(builder.limit)
      options.explain.foreach(if (_) builder.explain())
      options.noCursorTimeout.foreach(builder.noCursorTimeout)

      builder
    }
  }

}
