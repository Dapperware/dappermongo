package zio.mongo

import com.mongodb.reactivestreams.client.MongoDatabase
import org.bson.BsonDocument
import zio.ZIO
import zio.bson.BsonEncoder
import zio.mongo.results.DeleteResult

import zio.interop.reactivestreams.publisherToStream

trait DeleteOps {

  def delete: DeleteBuilder[Collection]

}

trait DeleteBuilder[-R] {
  def one[U: BsonEncoder](u: U): ZIO[R, Throwable, DeleteResult]

  def many[U: BsonEncoder](u: U): ZIO[Collection, Throwable, DeleteResult]
}

object DeleteBuilder {
  private[mongo] class Impl(database: MongoDatabase) extends DeleteBuilder[Collection] {

    override def many[U: BsonEncoder](u: U): ZIO[Collection, Throwable, DeleteResult] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.sessionRef.getWith { session =>
          val coll  = database.getCollection(collection.name, classOf[BsonDocument])
          val query = BsonEncoder[U].toBsonValue(u).asDocument()

          session
            .fold(coll.deleteMany(query))(coll.deleteMany(_, query))
            .toZIOStream(2)
            .runHead
            .map(_.fold(DeleteResult.Unacknowledged)(new DeleteResult(_)))
        }
      }

    override def one[U: BsonEncoder](u: U): ZIO[Collection, Throwable, DeleteResult] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.sessionRef.getWith { session =>
          val coll  = database.getCollection(collection.name, classOf[BsonDocument])
          val query = BsonEncoder[U].toBsonValue(u).asDocument()

          session
            .fold(coll.deleteOne(query))(coll.deleteOne(_, query))
            .toZIOStream(2)
            .runHead
            .map(_.fold(DeleteResult.Unacknowledged)(new DeleteResult(_)))
        }
      }
  }
}
