package zio.mongo

import com.mongodb.MongoDriverInformation
import com.mongodb.reactivestreams.client.{MongoClients, MongoClient => JMongoClient}
import com.mongodb.reactivestreams.client.ClientSession
import zio.interop.reactivestreams.publisherToStream
import zio.stream.ZStream
import zio._

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
      .map(Impl)

  private[mongo] lazy val driverInformation =
    MongoDriverInformation
      .builder()
      .driverName("zio-mongo")
      .driverVersion("0.0.1")
      .driverPlatform("zio")
      .build()

  private case class Impl(client: JMongoClient) extends MongoClient {
    override def database(name: String): ZIO[Any, Throwable, Database] =
      ZIO.attempt(client.getDatabase(name)).map(Database.Impl)

    override def listDatabaseNames: ZStream[Any, Throwable, String] =
      ZStream.suspend(client.listDatabaseNames().toZIOStream())

    override def startSession: ZIO[Scope, Throwable, Session] =
      ZIO
        .fromAutoCloseable(
          client
            .startSession()
            .toZIOStream(2)
            .runHead
            .someOrFailException
        )
        .flatMap(Session.make)
  }

  private[mongo] val sessionRef =
    Unsafe.unsafe(implicit u => FiberRef.unsafe.make[Option[ClientSession]](None))
}
