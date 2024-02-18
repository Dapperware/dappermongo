package dappermongo

import scala.jdk.CollectionConverters._

import com.mongodb.reactivestreams.client.{ClientSession, MongoDatabase => JMongoDatabase}
import dappermongo.aggregate.Pipeline
import org.reactivestreams.Publisher
import zio.ZIO

import dappermongo.internal.PublisherOps
import reactivemongo.api.bson.msb.fromDocument

private[dappermongo] trait CollectionOps {

  def collection: CollectionOps.Builder

}

object CollectionOps {

  trait Builder {
    def createCollection(
      name: String,
      options: CreateCollectionOptions = CreateCollectionOptions.default
    ): ZIO[Any, Throwable, Unit]

    def createView(
      name: String,
      viewOn: String,
      pipeline: Pipeline,
      options: CreateViewOptions = CreateViewOptions.default
    ): ZIO[Any, Throwable, Unit]

    def drop(): ZIO[Collection, Throwable, Unit]
  }

  object Builder {
    def apply(database: JMongoDatabase, sessionStorage: SessionStorage[ClientSession]): Builder =
      Impl(database, sessionStorage)

    private case class Impl(database: JMongoDatabase, sessionStorage: SessionStorage[ClientSession]) extends Builder {
      override def createCollection(
        name: String,
        options: CreateCollectionOptions
      ): ZIO[Any, Throwable, Unit] =
        sessionStorage.get.flatMap { maybeSession =>
          maybeSession
            .fold[Publisher[Void]](database.createCollection(name, options.toJava))(session =>
              database.createCollection(session, name, options.toJava)
            )
            .empty
        }

      override def createView(
        name: String,
        viewOn: String,
        pipeline: Pipeline,
        options: CreateViewOptions
      ): ZIO[Any, Throwable, Unit] =
        sessionStorage.get.flatMap { maybeSession =>
          val encoded = pipeline.toList.map(_.map(fromDocument).asJava).get

          maybeSession
            .fold[Publisher[Void]](database.createView(name, viewOn, encoded, options.toJava))(session =>
              database.createView(session, name, viewOn, encoded, options.toJava)
            )
            .empty
        }

      override def drop(): ZIO[Collection, Throwable, Unit] =
        sessionStorage.get.flatMap { maybeSession =>
          maybeSession
            .fold[Publisher[Void]](database.drop())(database.drop)
            .empty
        }
    }
  }

}
