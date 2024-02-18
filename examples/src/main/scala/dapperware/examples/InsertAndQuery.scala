package dapperware.examples

import dappermongo.{Collection, Database, MongoClient}
import reactivemongo.api.bson.{BSONDocumentHandler, Macros, document}
import zio.{ZIO, ZIOAppDefault}

object InsertAndQuery extends ZIOAppDefault {
  case class Cookie(name: String, price: Double, quantity: Int)

  object Cookie {
    implicit val handler: BSONDocumentHandler[Cookie] = Macros.handler[Cookie]
  }

  val program = for {
    db <- ZIO.service[Database]
    _ <- db.insert.many(
           List(
             Cookie("oreo", 1.5, 10),
             Cookie("chips ahoy", 2.0, 5),
             Cookie("nutter butter", 1.75, 7)
           )
         )
    _ <- db.find(document("price" -> document("$lt" -> 2.0))).stream[Cookie]().runCollect.debug
  } yield ()

  val run = program.provide(
    MongoClient.local,
    Collection.named("cookies"),
    Database.named("example")
  )

}
