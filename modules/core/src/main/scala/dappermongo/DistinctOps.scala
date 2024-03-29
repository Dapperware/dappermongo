package dappermongo

import com.mongodb.reactivestreams.client.{ClientSession, DistinctPublisher, MongoDatabase}
import dappermongo.internal.{DocumentEncodedFn, traverseOption}
import java.util.concurrent.TimeUnit
import org.bson.RawBsonDocument
import reactivemongo.api.bson.msb._
import reactivemongo.api.bson.{BSON, BSONDocumentWriter, BSONReader, document}
import zio.stream.{ZSink, ZStream}
import zio.{Chunk, Duration, Task, ZIO}

import zio.interop.reactivestreams.publisherToStream

trait DistinctOps {

  def distinct(field: String): DistinctBuilder[Collection]

}

trait DistinctBuilder[-R] {
  def filter[T: BSONDocumentWriter](filter: T): DistinctBuilder[R]

  def maxTime(maxTime: Duration): DistinctBuilder[R]

  def collation(collation: Collation): DistinctBuilder[R]

  def batchSize(batchSize: Int): DistinctBuilder[R]

  def comment(comment: String): DistinctBuilder[R]

  def toSet[T: BSONReader]: ZIO[R, Throwable, Set[T]]

  def toChunk[T: BSONReader]: ZIO[R, Throwable, Chunk[T]]
}

object DistinctBuilder {

  private[dappermongo] def apply(
    mongoDatabase: MongoDatabase,
    field: String,
    sessionStorage: SessionStorage[ClientSession]
  ): DistinctBuilder[Collection] =
    Impl(mongoDatabase, field, sessionStorage)

  private case class DistinctBuilderOptions(
    filter: Option[DocumentEncodedFn] = None,
    maxTime: Option[Duration] = None,
    collation: Option[Collation] = None,
    batchSize: Option[Int] = None,
    comment: Option[String] = None
  )
  private case class Impl(
    database: MongoDatabase,
    field: String,
    sessionStorage: SessionStorage[ClientSession],
    options: DistinctBuilderOptions = DistinctBuilderOptions()
  ) extends DistinctBuilder[Collection] {
    override def filter[T](filter: T)(implicit ev: BSONDocumentWriter[T]): DistinctBuilder[Collection] =
      copy(options = options.copy(filter = Some(DocumentEncodedFn(ev.writeTry(filter)))))

    override def maxTime(maxTime: Duration): DistinctBuilder[Collection] =
      copy(options = options.copy(maxTime = Some(maxTime)))

    override def collation(collation: Collation): DistinctBuilder[Collection] =
      copy(options = options.copy(collation = Some(collation)))

    override def batchSize(batchSize: Int): DistinctBuilder[Collection] =
      copy(options = options.copy(batchSize = Some(batchSize)))

    override def comment(comment: String): DistinctBuilder[Collection] =
      copy(options = options.copy(comment = Some(comment)))

    override def toSet[T: BSONReader]: ZIO[Collection, Throwable, Set[T]] =
      to(ZSink.collectAllToSet[T])

    override def toChunk[T: BSONReader]: ZIO[Collection, Throwable, Chunk[T]] =
      to(ZSink.collectAll[T])

    private def to[T: BSONReader, Out](sink: ZSink[Any, Throwable, T, Nothing, Out]): ZIO[Collection, Throwable, Out] =
      ZIO.serviceWithZIO { collection =>
        sessionStorage.get.flatMap { session =>
          ZStream
            .unwrap(makePublisher(session, collection, options).map(_.toZIOStream()))
            .map(doc => BSON.read[T](doc).fold(Left(_), Right(_)))
            .absolve
            .run(sink)
        }
      }

    private def makePublisher(
      session: Option[ClientSession],
      collection: Collection,
      options: DistinctBuilderOptions
    ): Task[DistinctPublisher[RawBsonDocument]] = ZIO.fromTry {
      val coll = database.getCollection(collection.name, classOf[org.bson.RawBsonDocument])
      for {
        maybeFilter <- traverseOption(options.filter.map(_.apply()))
      } yield {
        val filter     = fromDocument(maybeFilter.getOrElse(document))
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
}
