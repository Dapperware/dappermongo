package dappermongo

import com.mongodb.reactivestreams.client.{ClientSession, MongoDatabase}
import dappermongo.internal.CollectionConversionsVersionSpecific
import dappermongo.results.{Result, Updated}
import org.bson.BsonDocument
import org.bson.conversions.Bson
import reactivemongo.api.bson.BSONDocumentWriter
import reactivemongo.api.bson.msb._
import zio.{Chunk, ZIO}

import dappermongo.internal.PublisherOps

trait UpdateOps {

  def update: UpdateBuilder[Collection]

}

trait UpdateBuilder[-R] {
  def one[Q: BSONDocumentWriter, U: BSONDocumentWriter](q: Q, u: U): ZIO[R, Throwable, Result[Updated]]

  def many[Q: BSONDocumentWriter, U: BSONDocumentWriter](q: Q, updates: Chunk[U]): ZIO[R, Throwable, Result[Updated]]
}

object UpdateBuilder extends CollectionConversionsVersionSpecific {

  def apply(database: MongoDatabase, sessionStorage: SessionStorage[ClientSession]): UpdateBuilder[Collection] =
    new Impl(database, sessionStorage)
  private class Impl(database: MongoDatabase, sessionStorage: SessionStorage[ClientSession])
      extends UpdateBuilder[Collection] {

    def many[Q, U](q: Q, updates: Chunk[U])(implicit
      evQ: BSONDocumentWriter[Q],
      evU: BSONDocumentWriter[U]
    ): ZIO[Collection, Throwable, Result[Updated]] =
      ZIO.serviceWithZIO { collection =>
        sessionStorage.get.flatMap { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonDocument])

          val query: Bson = evQ.writeTry(q).get
          val ups         = updates.map(u => fromDocument(evU.writeTry(u).get))

          session
            .fold(coll.updateMany(query, seqAsJava(ups)))(coll.updateMany(_, query, seqAsJava(ups)))
            .single
            .map(
              _.fold[Result[Updated]](Result.Unacknowledged)(result =>
                Result
                  .Acknowledged(Updated(result.getMatchedCount, result.getModifiedCount, Option(result.getUpsertedId)))
              )
            )
        }
      }

    override def one[Q, U](
      q: Q,
      u: U
    )(implicit evQ: BSONDocumentWriter[Q], evU: BSONDocumentWriter[U]): ZIO[Collection, Throwable, Result[Updated]] =
      ZIO.serviceWithZIO { collection =>
        sessionStorage.get.flatMap { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonDocument])

          val query: Bson  = evQ.writeTry(q).get
          val update: Bson = evU.writeTry(u).get

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
