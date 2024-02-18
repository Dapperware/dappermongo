package dappermongo

import scala.jdk.CollectionConverters._

import com.mongodb.client.model.{CreateIndexOptions, IndexModel, Indexes}
import com.mongodb.reactivestreams.client.{ClientSession, MongoDatabase}
import java.util
import org.bson.conversions.Bson
import reactivemongo.api.bson.BSONDocumentReader
import reactivemongo.api.bson.msb._
import zio.ZIO
import zio.stream.ZStream

import dappermongo.internal.PublisherOps
import zio.interop.reactivestreams.publisherToStream

trait IndexOps {

  def index: IndexBuilder[Collection]

}

trait IndexBuilder[-R] {

  def list[T: BSONDocumentReader]: ZStream[R, Throwable, T]

  def create(indexes: List[Index], options: CreateIndexOptions): ZIO[R, Throwable, Unit]

  def drop(indexName: String): ZIO[R, Throwable, Unit]

  def dropAll(): ZIO[R, Throwable, Unit]

}

object IndexBuilder {
  case class Impl(database: MongoDatabase, sessionStorage: SessionStorage[ClientSession])
      extends IndexBuilder[Collection] {
    override def list[T](implicit ev: BSONDocumentReader[T]): ZStream[Collection, Throwable, T] =
      for {
        session    <- ZStream.fromZIO(sessionStorage.get)
        collection <- ZStream.service[Collection]
        jcollection = database.getCollection(collection.name, classOf[Bson])
        index <- session
                   .fold(jcollection.listIndexes())(jcollection.listIndexes)
                   .toZIOStream()
                   .map { index =>
                     ev.readTry(toDocument(index.toBsonDocument()))
                       .fold(Left(_), Right(_))
                   }
                   .absolve
      } yield index

    override def create(indexes: List[Index], options: CreateIndexOptions): ZIO[Collection, Throwable, Unit] =
      for {
        session    <- sessionStorage.get
        collection <- ZIO.service[Collection]
        jcollection = database.getCollection(collection.name)
        jIndexes    = indexes.map(toIndexModel).asJava
        _ <- session
               .fold(jcollection.createIndexes(jIndexes, options))(
                 jcollection.createIndexes(_, jIndexes, options)
               )
               .empty
      } yield ()

    override def drop(indexName: String): ZIO[Collection, Throwable, Unit] =
      for {
        session    <- sessionStorage.get
        collection <- ZIO.service[Collection]
        jcollection = database.getCollection(collection.name)
        _ <- session
               .fold(jcollection.dropIndex(indexName))(
                 jcollection.dropIndex(_, indexName)
               )
               .empty
      } yield ()

    override def dropAll(): ZIO[Collection, Throwable, Unit] =
      for {
        session    <- sessionStorage.get
        collection <- ZIO.service[Collection]
        jcollection = database.getCollection(collection.name)
        _          <- session.fold(jcollection.dropIndexes())(jcollection.dropIndexes).empty
      } yield ()

    private def toIndexModel(index: Index): IndexModel = {
      def go(i: Index): Bson = i match {
        case Index.Ascending(fields)   => Indexes.ascending(fields: _*)
        case Index.Descending(fields)  => Indexes.descending(fields: _*)
        case Index.Hashed(field)       => Indexes.hashed(field)
        case Index.Text(field)         => Indexes.text(field)
        case Index.Geo2D(field)        => Indexes.geo2d(field)
        case Index.Geo2DSphere(fields) => Indexes.geo2dsphere(fields: _*)
        case Index.Compound(indexes) =>
          Indexes.compoundIndex(new util.ArrayList[Bson](indexes.map(go).asJava))
      }

      new IndexModel(go(index))
    }
  }
}

sealed trait Index

object Index {
  case class Ascending(fields: List[String]) extends Index

  case class Descending(fields: List[String]) extends Index

  case class Hashed(field: String) extends Index

  case class Text(field: String) extends Index

  case class Geo2D(field: String) extends Index

  case class Geo2DSphere(fields: List[String]) extends Index

  case class Compound(indexes: List[Index]) extends Index
}
