package dappermongo

import com.mongodb.reactivestreams.client.MongoDatabase
import dappermongo.internal.PublisherOps
import dappermongo.results.{InsertedOne, Result}
import org.bson.BsonValue
import zio.ZIO
import zio.bson.BsonEncoder

trait InsertOps {

  def insert: InsertBuilder[Collection]

}

trait InsertBuilder[-R] {
  def one[U: BsonEncoder](u: U): ZIO[R, Throwable, Result[InsertedOne]]
}

object InsertBuilder {
  private[dappermongo] class Impl(database: MongoDatabase) extends InsertBuilder[Collection] {
    override def one[U: BsonEncoder](u: U): ZIO[Collection, Throwable, Result[InsertedOne]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonValue])

          val value = BsonEncoder[U].toBsonValue(u)

          session
            .fold(coll.insertOne(value))(coll.insertOne(_, value))
            .single
            .map(
              _.fold[Result[InsertedOne]](Result.Unacknowledged)(result =>
                Result.Acknowledged(InsertedOne(Option(result.getInsertedId)))
              )
            )
        }
      }
  }
}
