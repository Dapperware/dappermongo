package dappermongo

import com.mongodb.reactivestreams.client.MongoDatabase
import dappermongo.results.{InsertedOne, Result}
import org.bson.conversions.Bson
import reactivemongo.api.bson.BSONDocumentWriter
import reactivemongo.api.bson.msb._
import zio.ZIO

import dappermongo.internal.PublisherOps

trait InsertOps {

  def insert: InsertBuilder[Collection]

}

trait InsertBuilder[-R] {
  def one[U: BSONDocumentWriter](u: U): ZIO[R, Throwable, Result[InsertedOne]]
}

object InsertBuilder {

  def apply(database: MongoDatabase): InsertBuilder[Collection] =
    new Impl(database)

  private class Impl(database: MongoDatabase) extends InsertBuilder[Collection] {
    override def one[U](u: U)(implicit ev: BSONDocumentWriter[U]): ZIO[Collection, Throwable, Result[InsertedOne]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          val coll = database
            .getCollection(collection.name, classOf[Bson])

          val value: Bson = ev.writeTry(u).get

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
