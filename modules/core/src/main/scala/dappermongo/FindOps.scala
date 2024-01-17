package dappermongo

import com.mongodb.reactivestreams.client.MongoDatabase
import org.bson.RawBsonDocument
import reactivemongo.api.bson.msb._
import reactivemongo.api.bson.{BSONDocumentReader, BSONDocumentWriter}
import zio.ZIO
import zio.stream.ZStream

import dappermongo.internal.PublisherOps
import zio.interop.reactivestreams.publisherToStream

trait FindOps {

  def find[Q: BSONDocumentWriter](q: Q): FindBuilder[Collection]

  def find[Q: BSONDocumentWriter, P: BSONDocumentWriter](q: Q, p: P): FindBuilder[Collection]

  def findAll: FindBuilder[Collection]

}

trait FindBuilder[-R] {
  def one[A](implicit ev: BSONDocumentReader[A]): ZIO[R, Throwable, Option[A]]

  def stream[A](limit: Option[Int] = None, chunkSize: Int = 101)(implicit
    ev: BSONDocumentReader[A]
  ): ZStream[R, Throwable, A]

  def explain[A](implicit ev: BSONDocumentReader[A]): ZIO[R, Throwable, Option[A]]

  def allowDiskUse(allowDiskUse: Boolean): FindBuilder[R]

  def collation(collation: Collation): FindBuilder[R]

  def comment(comment: String): FindBuilder[R]

  def hint(hint: String): FindBuilder[R]

  def noCursorTimeout(noCursorTimeout: Boolean): FindBuilder[R]

  def projection[P: BSONDocumentWriter](projection: P): FindBuilder[R]

  def skip(skip: Int): FindBuilder[R]

  def sort[S: BSONDocumentWriter](sort: S): FindBuilder[R]
}

object FindBuilder {

  def apply(database: MongoDatabase): FindBuilder[Collection] =
    Impl(database, QueryBuilderOptions())

  def apply(database: MongoDatabase, options: QueryBuilderOptions): FindBuilder[Collection] =
    Impl(database, options)

  private case class Impl(database: MongoDatabase, options: QueryBuilderOptions) extends FindBuilder[Collection] {
    override def one[A](implicit ev: BSONDocumentReader[A]): ZIO[Collection, Throwable, Option[A]] =
      ZIO.serviceWithZIO { collection =>
        makePublisher(collection, options, Some(1), 1)
          .first()
          .single
          .flatMap {
            case Some(doc) => ZIO.fromTry(ev.readTry(toValue(doc)).map(Some(_)))
            case None      => ZIO.none
          }
      }

    override def stream[A](limit: Option[Int], chunkSize: Int)(implicit
      ev: BSONDocumentReader[A]
    ): ZStream[Collection, Throwable, A] =
      (for {
        collection <- ZStream.service[Collection]
        doc        <- makePublisher(collection, options, limit, chunkSize).toZIOStream(chunkSize)
      } yield ev.readTry(doc).toEither).absolve

    override def explain[A](implicit ev: BSONDocumentReader[A]): ZIO[Collection, Throwable, Option[A]] =
      copy(options = options.copy(explain = Some(true))).one[A]

    override def allowDiskUse(allowDiskUse: Boolean): FindBuilder[Collection] =
      copy(options = options.copy(allowDiskUse = Some(allowDiskUse)))

    override def collation(collation: Collation): FindBuilder[Collection] =
      copy(options = options.copy(collation = Some(collation)))

    override def comment(comment: String): FindBuilder[Collection] =
      copy(options = options.copy(comment = Some(comment)))

    override def hint(hint: String): FindBuilder[Collection] =
      copy(options = options.copy(hint = Some(hint)))

    override def noCursorTimeout(noCursorTimeout: Boolean): FindBuilder[Collection] =
      copy(options = options.copy(noCursorTimeout = Some(noCursorTimeout)))

    override def projection[P](projection: P)(implicit ev: BSONDocumentWriter[P]): FindBuilder[Collection] =
      copy(options = options.copy(projection = Some(() => ev.writeTry(projection).get)))

    override def skip(skip: Int): FindBuilder[Collection] =
      copy(options = options.copy(skip = Some(skip)))

    override def sort[S](sort: S)(implicit ev: BSONDocumentWriter[S]): FindBuilder[Collection] =
      copy(options = options.copy(sort = Some(() => ev.writeTry(sort).get)))

    private def makePublisher(
      collection: Collection,
      options: QueryBuilderOptions,
      limit: Option[Int],
      batchSize: Int
    ) = {
      val underlying = database.getCollection(collection.name, classOf[RawBsonDocument])
      val sort       = options.sort.map(_.apply()).map(fromDocument)
      val projection = options.projection.map(_.apply()).map(fromDocument)
      val filter     = options.filter.map(_.apply()).map(fromDocument)
      val builder    = filter.fold(underlying.find())(underlying.find(_))

      builder
        .sort(sort.orNull)
        .projection(projection.orNull)
        .filter(filter.orNull)
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
