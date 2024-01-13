package zio.mongo

import com.mongodb.client.model.CreateIndexOptions
import com.mongodb.reactivestreams.client.MongoDatabase
import org.bson.BsonDocument
import zio.{Chunk, ZIO}
import zio.bson.BsonDecoder
import zio.interop.reactivestreams.publisherToStream
import zio.stream.ZStream

trait IndexOps {

  def index: IndexBuilder[Collection]

}

trait IndexBuilder[-R] {

  def list[T: BsonDecoder]: ZStream[R, Throwable, T]

  def create(indexes: List[Index], options: CreateIndexOptions): ZIO[R, Throwable, String]

  def drop(indexName: String): ZIO[R, Throwable, Unit]

  def dropAll(): ZIO[R, Throwable, Unit]

}

object IndexBuilder {
  case class Impl(database: MongoDatabase) extends IndexBuilder[Collection] {
    override def list[T: BsonDecoder]: ZStream[Collection, Throwable, T] =
      for {
        session    <- ZStream.fromZIO(MongoClient.currentSession)
        collection <- ZStream.service[Collection]
        jcollection = database.getCollection(collection.name)
        index <- session
                   .fold(jcollection.listIndexes())(jcollection.listIndexes)
                   .toZIOStream()
                   .map(index => BsonDecoder[T].fromBsonValue(index.toBsonDocument()))
                   .absolve
      } yield index

    override def create(indexes: List[Index], options: CreateIndexOptions): ZIO[Collection, Throwable, String] =
      for {
        session    <- MongoClient.currentSession
        collection <- ZIO.service[Collection]
        jcollection = database.getCollection(collection.name)
//        _ <-
//          session
//            .fold(jcollection.createIndexes(indexes.map(_.toBsonDocument()), options))(
//              jcollection.createIndexes(_, indexes.map(_.toBsonDocument()), options)
//            )
//            .empty
      } yield ""

    override def drop(indexName: String): ZIO[Collection, Throwable, Unit] =
      ???

    override def dropAll(): ZIO[Collection, Throwable, Unit] =
      ???
  }
}

sealed trait Index {
  def toBsonDocument(): BsonDocument
}

object Index {
  case class Ascending(fields: List[String]) extends Index {
    override def toBsonDocument(): BsonDocument =
      new BsonDocument(fields.mkString("_", "_", "_1"), new BsonDocument())
  }

//  case class Descending(fields: List[String]) extends Index
//
//  case class Hashed(fields: String) extends Index
//
//  case class Text(field: String) extends Index
//
//  case class Geo2D(field: String) extends Index
//
//  case class Geo2DSphere(fields: List[String]) extends Index
//
//  case class GeoHaystack(field: String, additionalIndexes: List[Index]) extends Index
}
