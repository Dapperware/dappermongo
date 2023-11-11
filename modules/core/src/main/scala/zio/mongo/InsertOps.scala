package zio.mongo

import com.mongodb.reactivestreams.client.MongoDatabase
import org.bson.BsonValue
import zio.ZIO
import zio.bson.BsonEncoder

import zio.interop.reactivestreams.publisherToStream

trait InsertOps {

  def insert: InsertBuilder[Collection]

}

trait InsertBuilder[-R] {
  def one[U: BsonEncoder](u: U): ZIO[R, Throwable, Unit]
}

object InsertBuilder {
  private[mongo] class Impl(database: MongoDatabase) extends InsertBuilder[Collection] {
    override def one[U: BsonEncoder](u: U): ZIO[Collection, Throwable, Unit] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.sessionRef.getWith { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonValue])

          val value = BsonEncoder[U].toBsonValue(u)

          session
            .fold(coll.insertOne(value))(coll.insertOne(_, value))
            .toZIOStream(2)
            .runHead
            .unit
        }
      }
  }
}
