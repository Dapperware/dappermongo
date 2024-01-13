package dappermongo

import com.mongodb.reactivestreams.client.MongoDatabase
import dappermongo.internal.PublisherOps
import dappermongo.results.{Result, Updated}
import org.bson.BsonDocument
import zio.bson.BsonEncoder
import zio.{Chunk, ZIO}

import scala.jdk.CollectionConverters._

trait UpdateOps {

  def update: UpdateBuilder[Collection]

}

trait UpdateBuilder[-R] {
  def one[Q: BsonEncoder, U: BsonEncoder](q: Q, u: U): ZIO[R, Throwable, Result[Updated]]

  def many[Q: BsonEncoder, U: BsonEncoder](q: Q, updates: Chunk[U]): ZIO[R, Throwable, Result[Updated]]
}

object UpdateBuilder {
  private[dappermongo] class Impl(database: MongoDatabase) extends UpdateBuilder[Collection] {

    def many[Q: BsonEncoder, U: BsonEncoder](q: Q, updates: Chunk[U]): ZIO[Collection, Throwable, Result[Updated]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonDocument])

          val query  = BsonEncoder[Q].toBsonValue(q).asDocument()
          val update = updates.map(BsonEncoder[U].toBsonValue(_).asDocument()).asJava

          session
            .fold(coll.updateMany(query, update))(coll.updateMany(_, query, update))
            .single
            .map(
              _.fold[Result[Updated]](Result.Unacknowledged)(result =>
                Result
                  .Acknowledged(Updated(result.getMatchedCount, result.getModifiedCount, Option(result.getUpsertedId)))
              )
            )
        }
      }

    override def one[Q: BsonEncoder, U: BsonEncoder](q: Q, u: U): ZIO[Collection, Throwable, Result[Updated]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonDocument])

          val query  = BsonEncoder[Q].toBsonValue(q).asDocument()
          val update = BsonEncoder[U].toBsonValue(u).asDocument()

          session
            .fold(coll.updateOne(query, update))(coll.updateOne(_, query, update))
            .single
            .map(
              _.fold[Result[Updated]](Result.Unacknowledged)(result =>
                Result
                  .Acknowledged(Updated(result.getMatchedCount, result.getModifiedCount, Option(result.getUpsertedId)))
              )
            )
        }
      }
  }
}
