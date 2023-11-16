package zio.mongo

import scala.jdk.CollectionConverters._

import com.mongodb.reactivestreams.client.MongoDatabase
import org.bson.BsonDocument
import zio.bson.BsonEncoder
import zio.{Chunk, ZIO}

import zio.interop.reactivestreams.publisherToStream

trait UpdateOps {

  def update: UpdateBuilder[Collection]

}

trait UpdateBuilder[-R] {
  def one[Q: BsonEncoder, U: BsonEncoder](q: Q, u: U): ZIO[R, Throwable, Unit]
}

object UpdateBuilder {
  private[mongo] class Impl(database: MongoDatabase) extends UpdateBuilder[Collection] {

    def many[Q: BsonEncoder, U: BsonEncoder](q: Q, updates: Chunk[U]): ZIO[Collection, Throwable, Unit] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.sessionRef.getWith { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonDocument])

          val query  = BsonEncoder[Q].toBsonValue(q).asDocument()
          val update = updates.map(BsonEncoder[U].toBsonValue(_).asDocument()).asJava

          session
            .fold(coll.updateMany(query, update))(coll.updateMany(_, query, update))
            .toZIOStream(2)
            .runHead
            .unit
        }
      }

    override def one[Q: BsonEncoder, U: BsonEncoder](q: Q, u: U): ZIO[Collection, Throwable, Unit] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.sessionRef.getWith { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonDocument])

          val query  = BsonEncoder[Q].toBsonValue(q).asDocument()
          val update = BsonEncoder[U].toBsonValue(u).asDocument()

          session
            .fold(coll.updateOne(query, update))(coll.updateOne(_, query, update))
            .toZIOStream(2)
            .runHead
            .unit
        }
      }
  }
}
