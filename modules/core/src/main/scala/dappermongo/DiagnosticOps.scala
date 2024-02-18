package dappermongo

import com.mongodb.reactivestreams.client.{MongoDatabase => JMongoDatabase}
import dappermongo.internal.traverseOption
import org.bson.{BsonDocument, BsonInt32, RawBsonDocument}
import reactivemongo.api.bson.BSON
import reactivemongo.api.bson.msb._
import zio.ZIO

import dappermongo.internal.PublisherOps

private[dappermongo] trait DiagnosticOps {

  def diagnostics: DiagnosticBuilder

}

trait DiagnosticBuilder {
  def buildInfo: ZIO[Any, Throwable, Option[BuildInfo]]

  def ping: ZIO[Any, Throwable, Unit]
}

private[dappermongo] object DiagnosticBuilder {

  def apply(db: JMongoDatabase): DiagnosticBuilder = Impl(db)

  private case class Impl(db: JMongoDatabase) extends DiagnosticBuilder {
    override def buildInfo: ZIO[Any, Throwable, Option[BuildInfo]] =
      ZIO.suspend(
        db.runCommand(new BsonDocument("buildInfo", new BsonInt32(1)), classOf[RawBsonDocument])
          .single
          .flatMap(raw => ZIO.fromTry(traverseOption(raw.map(toDocument(_)).map(BSON.readDocument[BuildInfo]))))
      )

    override def ping: ZIO[Any, Throwable, Unit] =
      ZIO.suspend(
        db.runCommand(new BsonDocument("ping", new BsonInt32(1))).single.unit
      )
  }

}
