package zio.mongo

import com.mongodb.reactivestreams.client.{ClientSession, DistinctPublisher, MongoDatabase}
import org.bson.RawBsonDocument
import zio.bson.{BsonDecoder, BsonEncoder}
import zio.interop.reactivestreams.publisherToStream
import zio.stream.ZSink
import zio.{Chunk, Duration, ZIO}

import java.util.concurrent.TimeUnit

trait DistinctOps {

  def distinct(field: String): DistinctBuilder[Collection]

}

trait DistinctBuilder[-R] {
  def filter[T: BsonEncoder](filter: T): DistinctBuilder[R]

  def maxTime(maxTime: Duration): DistinctBuilder[R]

  def collation(collation: Collation): DistinctBuilder[R]

  def batchSize(batchSize: Int): DistinctBuilder[R]

  def comment(comment: String): DistinctBuilder[R]

  def toSet[T: BsonDecoder]: ZIO[R, Throwable, Set[T]]

  def toChunk[T: BsonDecoder]: ZIO[R, Throwable, Chunk[T]]
}

object DistinctBuilder {

  private[mongo] def apply(mongoDatabase: MongoDatabase, field: String): DistinctBuilder[Collection] =
    Impl(mongoDatabase, field)

  private case class DistinctBuilderOptions(
    filter: Option[() => org.bson.BsonDocument] = None,
    maxTime: Option[Duration] = None,
    collation: Option[Collation] = None,
    batchSize: Option[Int] = None,
    comment: Option[String] = None
  )
  private case class Impl(
    database: MongoDatabase,
    field: String,
    options: DistinctBuilderOptions = DistinctBuilderOptions()
  ) extends DistinctBuilder[Collection] {
    override def filter[T: BsonEncoder](filter: T): DistinctBuilder[Collection] =
      copy(options = options.copy(filter = Some(() => BsonEncoder[T].toBsonValue(filter).asDocument())))

    override def maxTime(maxTime: Duration): DistinctBuilder[Collection] =
      copy(options = options.copy(maxTime = Some(maxTime)))

    override def collation(collation: Collation): DistinctBuilder[Collection] =
      copy(options = options.copy(collation = Some(collation)))

    override def batchSize(batchSize: Int): DistinctBuilder[Collection] =
      copy(options = options.copy(batchSize = Some(batchSize)))

    override def comment(comment: String): DistinctBuilder[Collection] =
      copy(options = options.copy(comment = Some(comment)))

    override def toSet[T: BsonDecoder]: ZIO[Collection, Throwable, Set[T]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          makePublisher(session, collection, options)
            .toZIOStream()
            .map(BsonDecoder[T].fromBsonValue)
            .absolve
            .run(ZSink.collectAllToSet)
        }
      }

    override def toChunk[T: BsonDecoder]: ZIO[Collection, Throwable, Chunk[T]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          makePublisher(session, collection, options)
            .toZIOStream()
            .map(BsonDecoder[T].fromBsonValue)
            .absolve
            .runCollect
        }
      }

    private def makePublisher(
      session: Option[ClientSession],
      collection: Collection,
      options: DistinctBuilderOptions
    ): DistinctPublisher[RawBsonDocument] = {
      val coll       = database.getCollection(collection.name, classOf[org.bson.RawBsonDocument])
      val filter     = options.filter.map(_.apply()).getOrElse(new org.bson.BsonDocument())
      val collation0 = options.collation.map(_.asJava).orNull
      val publisher = {
        session.fold(coll.distinct(field, filter, classOf[RawBsonDocument]))(
          coll.distinct(_, field, filter, classOf[RawBsonDocument])
        )
      }

      options.batchSize.foreach(publisher.batchSize)
      options.comment.foreach(publisher.comment)
      options.maxTime.foreach(d => publisher.maxTime(d.toMillis, TimeUnit.MILLISECONDS))
      publisher.collation(collation0)

      publisher
    }
  }
}
