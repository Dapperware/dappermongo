package zio.mongo

import com.mongodb.MongoDriverInformation
import com.mongodb.reactivestreams.client.{ClientSession, MongoClient => JMongoClient, MongoClients}
import zio._
import zio.mongo.internal._
import zio.stream.ZStream

import zio.interop.reactivestreams.publisherToStream

trait MongoClient {

  def database(name: String): ZIO[Any, Throwable, Database]

  def listDatabaseNames: ZStream[Any, Throwable, String]

  def startSession: ZIO[Scope, Throwable, Session]

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
      ZIO.attempt(client.getDatabase(name)).map(Database.Impl.apply)

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

  private[mongo] val stateRef =
    Unsafe.unsafe(implicit u => FiberRef.unsafe.make[State](State(None, transacting = false)))

  private[mongo] val currentSession: ZIO[Any, Nothing, Option[ClientSession]] =
    stateRef.get.map(state => state.session.filter(_ => state.transacting))

  private[mongo] case class State(session: Option[ClientSession], transacting: Boolean)

}
