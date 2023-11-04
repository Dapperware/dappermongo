package zio.mongo

import zio.{Scope, ZIO, ZLayer}
import zio.stream.ZStream

trait MongoClient {

  def database(name: String): ZIO[Any, Throwable, Database]

  def listDatabaseNames: ZStream[Any, Throwable, String]

}

object MongoClient {

  // Constructors
  def configured: ZLayer[Any, Throwable, MongoClient] =
    ZLayer.scoped(for {
      config <- ZIO.config(MongoSettings.config)
      mongo  <- fromSettings(config)
    } yield mongo)

  val live: ZLayer[MongoSettings, Throwable, MongoClient] =
    ZLayer.scoped(scoped)

  def scoped: ZIO[Scope with MongoSettings, Throwable, MongoClient] = for {
    settings <- ZIO.service[MongoSettings]
    mongo    <- fromSettings(settings)
  } yield mongo

  def fromSettings(settings: MongoSettings): ZIO[Scope, Throwable, MongoClient] = ZIO.suspend {}
}
