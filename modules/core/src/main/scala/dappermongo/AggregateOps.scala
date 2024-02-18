package dappermongo
import scala.jdk.CollectionConverters._

import com.mongodb.reactivestreams.client.{ClientSession, MongoDatabase}
import dappermongo.aggregate.Pipeline
import dappermongo.internal._
import org.bson.RawBsonDocument
import reactivemongo.api.bson.msb._
import reactivemongo.api.bson.{BSON, BSONDocumentReader, BSONDocumentWriter}
import zio.stream.ZStream
import zio.{Duration, ZIO}

import zio.interop.reactivestreams.publisherToStream

trait AggregateOps {

  def aggregate(pipeline: Pipeline): AggregateBuilder[Collection]

}

trait AggregateBuilder[-R] {

  def allowDiskUse(allowDiskUse: Boolean): AggregateBuilder[R]

  def batchSize(batchSize: Int): AggregateBuilder[R]

  def bypassDocumentValidation(bypassDocumentValidation: Boolean): AggregateBuilder[R]

  def collation(collation: Option[Collation]): AggregateBuilder[R]

  def comment[T: BSONDocumentWriter](comment: T): AggregateBuilder[R]

  def hint[T: BSONDocumentWriter](hint: T): AggregateBuilder[R]

  def maxTime(maxTime: Duration): AggregateBuilder[R]

  def maxAwaitTime(maxAwaitTime: Duration): AggregateBuilder[R]

  /**
   * Runs the aggregation pipeline into another collection.
   */
  def out(collection: Collection): ZIO[R, Throwable, Unit]

  def one[T: BSONDocumentReader]: ZIO[R, Throwable, Option[T]]

  def stream[T: BSONDocumentReader]: ZStream[R, Throwable, T]
}

object AggregateBuilder {
  def apply(
    database: MongoDatabase,
    pipeline: Pipeline,
    sessionStorage: SessionStorage[ClientSession]
  ): AggregateBuilder[Collection] =
    Impl(database, pipeline, AggregateBuilderOptions(), sessionStorage)

  private case class Impl(
    database: MongoDatabase,
    pipeline: Pipeline,
    options: AggregateBuilderOptions,
    sessionStorage: SessionStorage[ClientSession]
  ) extends AggregateBuilder[Collection] {

    override def allowDiskUse(allowDiskUse: Boolean): AggregateBuilder[Collection] =
      copy(options = options.copy(allowDiskUse = Some(allowDiskUse)))

    override def batchSize(batchSize: Int): AggregateBuilder[Collection] =
      copy(options = options.copy(batchSize = Some(batchSize)))

    override def bypassDocumentValidation(bypassDocumentValidation: Boolean): AggregateBuilder[Collection] =
      copy(options = options.copy(bypassDocumentValidation = Some(bypassDocumentValidation)))

    override def collation(collation: Option[Collation]): AggregateBuilder[Collection] =
      copy(options = options.copy(collation = collation))

    override def comment[T: BSONDocumentWriter](comment: T): AggregateBuilder[Collection] =
      copy(options = options.copy(comment = Some(DocumentEncodedFn(BSON.writeDocument(comment)))))

    override def hint[T: BSONDocumentWriter](hint: T): AggregateBuilder[Collection] =
      copy(options = options.copy(hint = Some(DocumentEncodedFn(BSON.writeDocument(hint)))))

    override def maxTime(maxTime: Duration): AggregateBuilder[Collection] =
      copy(options = options.copy(maxTime = Some(maxTime)))

    override def maxAwaitTime(maxAwaitTime: Duration): AggregateBuilder[Collection] =
      copy(options = options.copy(maxAwaitTime = Some(maxAwaitTime)))

    override def out(collection: Collection): ZIO[Collection, Throwable, Unit] =
      ZIO.serviceWithZIO { collection =>
        makePublisher(collection, pipeline, options, None).flatMap(_.empty)
      }

    override def one[T](implicit ev: BSONDocumentReader[T]): ZIO[Collection, Throwable, Option[T]] =
      ZIO.serviceWithZIO { collection =>
        sessionStorage.get.flatMap { session =>
          makePublisher(collection, pipeline, options, session).flatMap {
            _.first().single.flatMap {
              case Some(doc) => ZIO.fromTry(ev.readTry(toValue(doc)).map(Some(_)))
              case None      => ZIO.none
            }
          }
        }
      }

    override def stream[T](implicit ev: BSONDocumentReader[T]): ZStream[Collection, Throwable, T] =
      (for {
        collection <- ZStream.service[Collection]
        publisher  <- ZStream.fromZIO(makePublisher(collection, pipeline, options, None))
        doc        <- publisher.toZIOStream()
      } yield ev.readTry(doc).toEither).absolve

    private def makePublisher(
      collection: Collection,
      pipeline: Pipeline,
      options: AggregateBuilderOptions,
      session: Option[ClientSession]
    ) = ZIO.attempt {
      val underlying = database.getCollection(collection.name, classOf[RawBsonDocument])
      val encoded    = pipeline.toList.map(_.map(fromDocument).asJava).get

      val hint    = options.hint.map(_.apply()).map(_.map(fromDocument).get)
      val comment = options.comment.map(_.apply()).map(_.map(fromDocument).get)

      val builder = session
        .fold(underlying.aggregate(encoded))(underlying.aggregate(_, encoded))
        .hint(hint.orNull)
        .allowDiskUse(options.allowDiskUse.map(Boolean.box).orNull)
        .bypassDocumentValidation(options.bypassDocumentValidation.map(Boolean.box).orNull)
        .maxTime(options.maxTime.map(_.toMillis).getOrElse(0L), java.util.concurrent.TimeUnit.MILLISECONDS)
        .maxAwaitTime(options.maxAwaitTime.map(_.toMillis).getOrElse(0L), java.util.concurrent.TimeUnit.MILLISECONDS)
        .comment(comment.orNull)

      builder
    }
  }

  private case class AggregateBuilderOptions(
    allowDiskUse: Option[Boolean] = None,
    batchSize: Option[Int] = None,
    bypassDocumentValidation: Option[Boolean] = None,
    collation: Option[Collation] = None,
    comment: Option[DocumentEncodedFn] = None,
    hint: Option[DocumentEncodedFn] = None,
    maxTime: Option[Duration] = None,
    maxAwaitTime: Option[Duration] = None
  )
}
