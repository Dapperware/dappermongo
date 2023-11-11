package zio.mongo

import zio.{Random, Scope, ZIO, ZLayer}
import zio.bson.magnolia.DeriveBsonCodec
import zio.test._

object TransactionSpec extends MongoITSpecDefault {

  private val randomCollection =
    ZLayer(Random.nextIntBetween(0, Int.MaxValue).map(value => Collection(s"test-$value")))

  val spec = suite("Transactions")(
    test("simple - transactionally") {
      case class Person(str: String, i: Int)

      implicit val personCodec = DeriveBsonCodec.derive[Person]

      for {
        client  <- ZIO.service[MongoClient]
        session <- client.startSession
        db      <- Database.make("test")
        p1 <- session.transactional {
                for {
                  _  <- db.insert.one(Person("John", 42))
                  p1 <- db.findAll.stream[Person]().runCollect
                  _  <- db.insert.one(Person("Jane", 43))
                } yield p1
              }
        p2 <- db.findAll.stream[Person]().runCollect
      } yield assertTrue(p1.isEmpty, p2.size == 2)
    },
    test("simple - scoped") {
      case class Person(str: String, i: Int)
      implicit val personCodec = DeriveBsonCodec.derive[Person]

      for {
        client  <- ZIO.service[MongoClient]
        session <- client.startSession
        db      <- Database.make("test")
        p1 <- ZIO.scoped(for {
                _ <- session.transactionScoped
                p1 <- for {
                        _  <- db.insert.one(Person("John", 42))
                        p1 <- db.findAll.stream[Person]().runCollect
                        _  <- db.insert.one(Person("Jane", 43))
                      } yield p1
              } yield p1)
        p2 <- db.findAll.stream[Person]().runCollect
      } yield assertTrue(p1.isEmpty, p2.size == 2)
    }
  ).provideSome[Scope with MongoClient](randomCollection) @@ TestAspect.withLiveRandom @@ TestAspect.sequential

}
