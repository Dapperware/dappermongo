package zio.mongo

import com.mongodb.reactivestreams.client.MongoDatabase
import java.util.concurrent.TimeUnit
import org.bson.{BsonDocument, BsonValue}
import zio.bson.BsonEncoder
import zio.{Duration, ZIO}

import zio.mongo.internal.PublisherOps

trait CountOps {

  def count: CountBuilder[Collection]

}

trait CountBuilder[-R] {
  def filter[T: BsonEncoder](filter: T): CountBuilder[R]

  def limit(limit: Int): CountBuilder[R]

  def maxTime(maxTime: Duration): CountBuilder[R]

  def skip(skip: Int): CountBuilder[R]

  def hint[T: BsonEncoder](hint: T): CountBuilder[R]

  def collation(collation: Collation): CountBuilder[R]

  def comment(comment: String): CountBuilder[R]

  def count: ZIO[R, Throwable, Long]

}

object CountBuilder {
  case class Options(
    filter: Option[() => BsonDocument] = None,
    limit: Option[Int] = None,
    maxTime: Option[Duration] = None,
    skip: Option[Int] = None,
    hint: Option[() => BsonValue] = None,
    collation: Option[Collation] = None,
    comment: Option[String] = None
  )

  private case class Impl(private val db: MongoDatabase, private val options: Options)
      extends CountBuilder[Collection] {
    override def filter[T: BsonEncoder](filter: T): CountBuilder[Collection] =
      copy(options = options.copy(filter = Some(() => BsonEncoder[T].toBsonValue(filter).asDocument())))

    override def limit(limit: Int): CountBuilder[Collection] =
      copy(options = options.copy(limit = Some(limit)))

    override def maxTime(maxTime: Duration): CountBuilder[Collection] =
      copy(options = options.copy(maxTime = Some(maxTime)))

    override def skip(skip: Int): CountBuilder[Collection] =
      copy(options = options.copy(skip = Some(skip)))

    override def hint[T: BsonEncoder](hint: T): CountBuilder[Collection] =
      copy(options = options.copy(hint = Some(() => BsonEncoder[T].toBsonValue(hint))))

    override def collation(collation: Collation): CountBuilder[Collection] =
      copy(options = options.copy(collation = Some(collation)))

    override def comment(comment: String): CountBuilder[Collection] =
      copy(options = options.copy(comment = Some(comment)))

    override def count: ZIO[Collection, Throwable, Long] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          val coll         = db.getCollection(collection.name)
          val query        = options.filter.map(_.apply()).getOrElse(new BsonDocument())
          val countOptions = new com.mongodb.client.model.CountOptions()
          options.limit.foreach(countOptions.limit)
          options.maxTime.foreach(d => countOptions.maxTime(d.toMillis, TimeUnit.MILLISECONDS))
          options.skip.foreach(countOptions.skip)
          // TODO - how do we convert this into a `Bson`?
          options.hint.map(_.apply()).foreach(hint => countOptions.hint(hint.asDocument()))
          options.collation.foreach(collation => countOptions.collation(collation.asJava))
          options.comment.foreach(countOptions.comment)
          session
            .fold(coll.countDocuments(query, countOptions))(coll.countDocuments(_, query, countOptions))
            .single
            .map(_.fold(0L)(Long.box(_))) // TODO should this throw?
        }
      }
  }
}
