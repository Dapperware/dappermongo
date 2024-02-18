package dappermongo

import com.mongodb.MongoDriverInformation
import com.mongodb.reactivestreams.client.{ClientSession, MongoClient => JMongoClient, MongoClients}
import zio._
import zio.stream.ZStream

import dappermongo.internal.PublisherOps
import zio.interop.reactivestreams.publisherToStream

trait MongoClient {

  def database(name: String): ZIO[Any, Throwable, Database]

  def listDatabaseNames: ZStream[Any, Throwable, String]

  def startSession: ZIO[Scope, Throwable, Session]

  def dropDatabase(name: String): ZIO[Any, Throwable, Unit]

}

object MongoClient {

  /**
   * Constructs a `MongoClient` from the local `MongoSettings`.
   */
  def local(implicit trace: Trace): ZLayer[Any, Throwable, MongoClient] =
    ZLayer.scoped(fromSettings(MongoSettings.local))

  /**
   * Constructs a `MongoClient` by loading the settings from the built-in
   * `ConfigProvider`, using the default path of `mongodb`.
   */
  def configured(implicit trace: Trace): ZLayer[Any, Throwable, MongoClient] =
    configured(NonEmptyChunk.single("mongodb"))

  /**
   * Constructs a `MongoClient` by loading the settings from the built-in
   * `ConfigProvider`, using the specified path.
   */
  def configured(at: NonEmptyChunk[String])(implicit trace: Trace): ZLayer[Any, Throwable, MongoClient] =
    ZLayer.scoped(for {
      config <- ZIO.config(MongoSettings.config.nested(at.head, at.tail: _*))
      mongo  <- fromSettings(config)
    } yield mongo)

  /**
   * Constructs a `MongoClient` from the environmental `MongoSettings`.
   */
  def live(implicit trace: Trace): ZLayer[MongoSettings, Throwable, MongoClient] =
    ZLayer.scoped(scoped)

  /**
   * Constructs a `MongoClient` from the environmental `MongoSettings` and
   * provides it as a Scoped effect
   */
  def scoped(implicit trace: Trace): ZIO[Scope with MongoSettings, Throwable, MongoClient] = for {
    settings <- ZIO.service[MongoSettings]
    mongo    <- fromSettings(settings)
  } yield mongo

  /**
   * Constructs a `MongoClient` from the specified `MongoSettings`.
   */
  def fromSettings(settings: MongoSettings)(implicit trace: Trace): ZIO[Scope, Throwable, MongoClient] =
    ZIO
      .fromAutoCloseable(ZIO.attempt(MongoClients.create(settings.toJava, driverInformation)))
      .flatMap(client => SessionStorage.fiberRef[ClientSession].map(Impl.apply(client, _)))

  private lazy val driverInformation =
    MongoDriverInformation
      .builder()
      .driverName("dappermongo")
      .driverVersion("0.0.1")
      .driverPlatform("zio")
      .build()

  private case class Impl(client: JMongoClient, sessionStorage: SessionStorage[ClientSession]) extends MongoClient {
    override def database(name: String): ZIO[Any, Throwable, Database] =
      ZIO.attempt(client.getDatabase(name)).map(Database.apply(_, sessionStorage))

    override def listDatabaseNames: ZStream[Any, Throwable, String] =
      ZStream.unwrap(sessionStorage.get.map(_.fold(client.listDatabaseNames())(client.listDatabaseNames).toZIOStream()))

    override def startSession: ZIO[Scope, Throwable, Session] =
      ZIO
        .fromAutoCloseable(
          client
            .startSession()
            .single
            .someOrFailException
        )
        .map(Session.apply(_, sessionStorage))

    override def dropDatabase(name: String): ZIO[Any, Throwable, Unit] =
      ZIO.suspend(client.getDatabase(name).drop().empty)
  }
}
