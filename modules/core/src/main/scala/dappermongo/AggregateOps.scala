package dappermongo

import scala.util.Try

import com.mongodb.reactivestreams.client.{ClientSession, MongoDatabase}
import dappermongo.aggregate.Pipeline
import dappermongo.internal.{CollectionConversionsVersionSpecific, _}
import org.bson.RawBsonDocument
import reactivemongo.api.bson.msb._
import reactivemongo.api.bson.{BSON, BSONDocumentReader, BSONDocumentWriter, BSONValue, BSONWriter}
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

  def comment[T: BSONWriter](comment: T): AggregateBuilder[R]

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
  def apply(database: MongoDatabase, pipeline: Pipeline): AggregateBuilder[Collection] =
    Impl(database, pipeline, AggregateBuilderOptions())

  private case class Impl(database: MongoDatabase, pipeline: Pipeline, options: AggregateBuilderOptions)
      extends AggregateBuilder[Collection]
      with CollectionConversionsVersionSpecific {

    override def allowDiskUse(allowDiskUse: Boolean): AggregateBuilder[Collection] =
      copy(options = options.copy(allowDiskUse = Some(allowDiskUse)))

    override def batchSize(batchSize: Int): AggregateBuilder[Collection] =
      copy(options = options.copy(batchSize = Some(batchSize)))

    override def bypassDocumentValidation(bypassDocumentValidation: Boolean): AggregateBuilder[Collection] =
      copy(options = options.copy(bypassDocumentValidation = Some(bypassDocumentValidation)))

    override def collation(collation: Option[Collation]): AggregateBuilder[Collection] =
      copy(options = options.copy(collation = collation))

    override def comment[T: BSONWriter](comment: T): AggregateBuilder[Collection] =
      copy(options = options.copy(comment = Some(() => BSON.write(comment))))

    override def hint[T: BSONDocumentWriter](hint: T): AggregateBuilder[Collection] =
      copy(options = options.copy(hint = Some(() => BSON.write(hint))))

    override def maxTime(maxTime: Duration): AggregateBuilder[Collection] =
      copy(options = options.copy(maxTime = Some(maxTime)))

    override def maxAwaitTime(maxAwaitTime: Duration): AggregateBuilder[Collection] =
      copy(options = options.copy(maxAwaitTime = Some(maxAwaitTime)))

    override def out(collection: Collection): ZIO[Collection, Throwable, Unit] =
      ZIO.serviceWithZIO { collection =>
        makePublisher(collection, pipeline, options, None, None).empty
      }

    override def one[T](implicit ev: BSONDocumentReader[T]): ZIO[Collection, Throwable, Option[T]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          makePublisher(collection, pipeline, options, Some(1), session)
            .first()
            .single
            .flatMap {
              case Some(doc) => ZIO.fromTry(ev.readTry(toValue(doc)).map(Some(_)))
              case None      => ZIO.none
            }
        }
      }

    override def stream[T](implicit ev: BSONDocumentReader[T]): ZStream[Collection, Throwable, T] =
      (for {
        collection <- ZStream.service[Collection]
        doc        <- makePublisher(collection, pipeline, options, None, None).toZIOStream()
      } yield ev.readTry(doc).toEither).absolve

    private def makePublisher(
      collection: Collection,
      pipeline: Pipeline,
      options: AggregateBuilderOptions,
      limit: Option[Int],
      session: Option[ClientSession]
    ) = {
      val underlying = database.getCollection(collection.name, classOf[RawBsonDocument])
      val encoded = listAsJava(
        pipeline.stages.reduceMapLeft(stage => List(fromDocument(BSON.writeDocument(stage).get))) { (acc, stage) =>
          acc ++ List(fromDocument(BSON.writeDocument(stage).get))
        }
      )

      // TODO encode options

      val builder = session.fold(underlying.aggregate(encoded))(underlying.aggregate(_, encoded))

      builder
    }
  }

  private case class AggregateBuilderOptions(
    allowDiskUse: Option[Boolean] = None,
    batchSize: Option[Int] = None,
    bypassDocumentValidation: Option[Boolean] = None,
    collation: Option[Collation] = None,
    comment: Option[() => Try[BSONValue]] = None,
    hint: Option[() => Try[BSONValue]] = None,
    maxTime: Option[Duration] = None,
    maxAwaitTime: Option[Duration] = None
  )
}
