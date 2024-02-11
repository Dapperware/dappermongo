package dappermongo

import com.mongodb.reactivestreams.client.MongoDatabase
import dappermongo.internal.{DocumentEncodedFn, traverseOption}
import java.util.concurrent.TimeUnit
import org.bson.BsonDocument
import reactivemongo.api.bson.msb._
import reactivemongo.api.bson.{BSON, BSONDocumentWriter}
import zio.{Duration, ZIO}

import dappermongo.internal.PublisherOps

trait CountOps {

  def count: CountBuilder[Collection]

}

trait CountBuilder[-R] {
  def filter[T: BSONDocumentWriter](filter: T): CountBuilder[R]

  def limit(limit: Int): CountBuilder[R]

  def maxTime(maxTime: Duration): CountBuilder[R]

  def skip(skip: Int): CountBuilder[R]

  def hint[T: BSONDocumentWriter](hint: T): CountBuilder[R]

  def collation(collation: Collation): CountBuilder[R]

  def comment(comment: String): CountBuilder[R]

  def count: ZIO[R, Throwable, Long]

}

object CountBuilder {

  def apply(database: MongoDatabase): CountBuilder[Collection] =
    Impl(database, Options())

  private case class Options(
    filter: Option[DocumentEncodedFn] = None,
    limit: Option[Int] = None,
    maxTime: Option[Duration] = None,
    skip: Option[Int] = None,
    hint: Option[DocumentEncodedFn] = None,
    collation: Option[Collation] = None,
    comment: Option[String] = None
  )

  private case class Impl(private val db: MongoDatabase, private val options: Options)
      extends CountBuilder[Collection] {
    override def filter[T: BSONDocumentWriter](filter: T): CountBuilder[Collection] =
      copy(options = options.copy(filter = Some(DocumentEncodedFn(BSON.writeDocument(filter)))))

    override def limit(limit: Int): CountBuilder[Collection] =
      copy(options = options.copy(limit = Some(limit)))

    override def maxTime(maxTime: Duration): CountBuilder[Collection] =
      copy(options = options.copy(maxTime = Some(maxTime)))

    override def skip(skip: Int): CountBuilder[Collection] =
      copy(options = options.copy(skip = Some(skip)))

    override def hint[T: BSONDocumentWriter](hint: T): CountBuilder[Collection] =
      copy(options = options.copy(hint = Some(DocumentEncodedFn(BSON.writeDocument(hint)))))

    override def collation(collation: Collation): CountBuilder[Collection] =
      copy(options = options.copy(collation = Some(collation)))

    override def comment(comment: String): CountBuilder[Collection] =
      copy(options = options.copy(comment = Some(comment)))

    override def count: ZIO[Collection, Throwable, Long] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          ZIO
            .fromTry(for {
              maybeQuery <- traverseOption(options.filter.map(_.apply()))
              hint       <- traverseOption(options.hint.map(_.apply()))
            } yield {
              val coll         = db.getCollection(collection.name)
              val countOptions = new com.mongodb.client.model.CountOptions()
              val query        = maybeQuery.fold(new BsonDocument())(fromDocument)
              options.limit.foreach(countOptions.limit)
              options.maxTime.foreach(d => countOptions.maxTime(d.toMillis, TimeUnit.MILLISECONDS))
              options.skip.foreach(countOptions.skip)
              hint.foreach(hint => countOptions.hint(fromDocument(hint)))
              options.collation.foreach(collation => countOptions.collation(collation.asJava))
              options.comment.foreach(countOptions.comment)
              session
                .fold(coll.countDocuments(query, countOptions))(coll.countDocuments(_, query, countOptions))
                .single
                .map(_.fold(0L)(Long.box(_)))
            })
            .flatten
        }
      }
  }
}
