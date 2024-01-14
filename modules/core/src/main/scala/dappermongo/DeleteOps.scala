package dappermongo

import com.mongodb.reactivestreams.client.MongoDatabase
import dappermongo.results.{Deleted, Result}
import org.bson.RawBsonDocument
import zio.ZIO
import zio.bson.BsonEncoder

import dappermongo.internal.PublisherOps

trait DeleteOps {

  def delete: DeleteBuilder[Collection]

}

trait DeleteBuilder[-R] {
  def one[U: BsonEncoder](u: U): ZIO[R, Throwable, Result[Deleted]]

  def many[U: BsonEncoder](u: U): ZIO[Collection, Throwable, Result[Deleted]]
}

object DeleteBuilder {

  def apply(database: MongoDatabase): DeleteBuilder[Collection] =
    new Impl(database)

  private class Impl(database: MongoDatabase) extends DeleteBuilder[Collection] {

    override def many[U: BsonEncoder](u: U): ZIO[Collection, Throwable, Result[Deleted]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          val coll  = database.getCollection(collection.name, classOf[RawBsonDocument])
          val query = BsonEncoder[U].toBsonValue(u).asDocument()

          session
            .fold(coll.deleteMany(query))(coll.deleteMany(_, query))
            .single
            .map(
              _.fold[Result[Deleted]](Result.Unacknowledged)(result =>
                Result.Acknowledged(Deleted(result.getDeletedCount))
              )
            )
        }
      }

    override def one[U: BsonEncoder](u: U): ZIO[Collection, Throwable, Result[Deleted]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          val coll  = database.getCollection(collection.name, classOf[RawBsonDocument])
          val query = BsonEncoder[U].toBsonValue(u).asDocument()

          session
            .fold(coll.deleteOne(query))(coll.deleteOne(_, query))
            .single
            .map(
              _.fold[Result[Deleted]](Result.Unacknowledged)(result =>
                Result.Acknowledged(Deleted(result.getDeletedCount))
              )
            )
        }
      }
  }
}
