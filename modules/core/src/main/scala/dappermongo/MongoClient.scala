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

}

object MongoClient {

  /**
   * Constructs a `MongoClient` by loading the settings from the built-in
   * `ConfigProvider`, using the default path of `mongodb`.
   */
  def configured: ZLayer[Any, Throwable, MongoClient] =
    configured(NonEmptyChunk.single("mongodb"))

  /**
   * Constructs a `MongoClient` by loading the settings from the built-in
   * `ConfigProvider`, using the specified path.
   */
  def configured(at: NonEmptyChunk[String]): ZLayer[Any, Throwable, MongoClient] =
    ZLayer.scoped(for {
      config <- ZIO.config(MongoSettings.config.nested(at.head, at.tail: _*))
      mongo  <- fromSettings(config)
    } yield mongo)

  /**
   * Constructs a `MongoClient` from the environmental `MongoSettings`.
   */
  val live: ZLayer[MongoSettings, Throwable, MongoClient] =
    ZLayer.scoped(scoped)

  /**
   * Constructs a `MongoClient` from the environmental `MongoSettings` and
   * provides it as a Scoped effect
   */
  def scoped: ZIO[Scope with MongoSettings, Throwable, MongoClient] = for {
    settings <- ZIO.service[MongoSettings]
    mongo    <- fromSettings(settings)
  } yield mongo

  /**
   * Constructs a `MongoClient` from the specified `MongoSettings`.
   */
  def fromSettings(settings: MongoSettings): ZIO[Scope, Throwable, MongoClient] =
    ZIO
      .fromAutoCloseable(ZIO.attempt(MongoClients.create(settings.toJava, driverInformation)))
      .map(Impl.apply)

  private lazy val driverInformation =
    MongoDriverInformation
      .builder()
      .driverName("dappermon")
      .driverVersion("0.0.1")
      .driverPlatform("zio")
      .build()

  private case class Impl(client: JMongoClient) extends MongoClient {
    override def database(name: String): ZIO[Any, Throwable, Database] =
      ZIO.attempt(client.getDatabase(name)).map(Database.apply)

    override def listDatabaseNames: ZStream[Any, Throwable, String] =
      ZStream.suspend(client.listDatabaseNames().toZIOStream())

    override def startSession: ZIO[Scope, Throwable, Session] =
      ZIO
        .fromAutoCloseable(
          client
            .startSession()
            .single
            .someOrFailException
        )
        .flatMap(Session.make)
  }

  private[dappermongo] val stateRef =
    Unsafe.unsafe(implicit u => FiberRef.unsafe.make[State](State(None, transacting = false)))

  private[dappermongo] val currentSession: ZIO[Any, Nothing, Option[ClientSession]] =
    stateRef.get.map(state => state.session.filter(_ => state.transacting))

  private[dappermongo] case class State(session: Option[ClientSession], transacting: Boolean)

}
