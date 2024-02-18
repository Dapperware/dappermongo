package dappermongo

import scala.jdk.CollectionConverters._

import com.mongodb.reactivestreams.client.{ClientSession, MongoDatabase}
import dappermongo.results.{InsertedMany, InsertedOne, Result}
import org.bson.BsonDocument
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

  def many[U: BSONDocumentWriter](us: List[U]): ZIO[R, Throwable, Result[InsertedMany]]
}

object InsertBuilder {

  def apply(database: MongoDatabase, sessionStorage: SessionStorage[ClientSession]): InsertBuilder[Collection] =
    new Impl(database, sessionStorage)

  private class Impl(database: MongoDatabase, sessionStorage: SessionStorage[ClientSession])
      extends InsertBuilder[Collection] {
    override def one[U](u: U)(implicit ev: BSONDocumentWriter[U]): ZIO[Collection, Throwable, Result[InsertedOne]] =
      ZIO.serviceWithZIO { collection =>
        sessionStorage.get.flatMap { session =>
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

    override def many[U](
      us: List[U]
    )(implicit ev: BSONDocumentWriter[U]): ZIO[Collection, Throwable, Result[InsertedMany]] =
      ZIO.serviceWithZIO { collection =>
        sessionStorage.get.flatMap { session =>
          val coll = database
            .getCollection(collection.name, classOf[BsonDocument])

          val values: java.util.List[BsonDocument] = us.map(u => fromDocument(ev.writeTry(u).get)).asJava

          session
            .fold(coll.insertMany(values))(coll.insertMany(_, values))
            .single
            .map(
              _.fold[Result[InsertedMany]](Result.Unacknowledged)(result =>
                Result.Acknowledged(
                  InsertedMany(
                    result.getInsertedIds.asScala.view.map(kv => (kv._1.intValue(), toValue(kv._2))).toMap
                  )
                )
              )
            )
        }
      }
  }
}
