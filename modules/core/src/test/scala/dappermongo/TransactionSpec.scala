package dappermongo

import reactivemongo.api.bson.Macros.Annotations.Key
import reactivemongo.api.bson.{BSONDocumentHandler, BSONHandler, Macros}
import zio.test._
import zio.{Chunk, Promise, Random, Scope, ZIO, ZLayer}

object TransactionSpec extends MongoITSpecDefault {

  private val randomCollection =
    ZLayer(Random.nextIntBetween(0, Int.MaxValue).map(value => Collection(s"test-$value")))

  case class Setter[A](@Key("$set") value: A)

  object Setter {
    implicit def codec[A: BSONHandler]: BSONDocumentHandler[Setter[A]] = {
      val _ = implicitly[BSONHandler[A]] // To satisfy scalafix
      Macros.handler[Setter[A]]
    }
  }

  case class Person(@Key("_id") name: String, age: Int)

  object Person {

    implicit val codec: BSONDocumentHandler[Person] = Macros.handler[Person]
    case class SetAge(age: Int)

    object SetAge {
      implicit val schema: BSONDocumentHandler[SetAge] = Macros.handler[SetAge]
    }
  }

  val spec = suite("Transactions")(
    test("simple - transactionally") {

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
      } yield assertTrue(p1.size == 1, p2.size == 2)
    },
    test("simple - scoped") {

      for {
        client  <- ZIO.service[MongoClient]
        session <- client.startSession
        db      <- Database.make("test")
        latch   <- Promise.make[Nothing, Unit]
        query    = db.findAll.stream[Person]().runCollect
        fiber <- ZIO
                   .scoped(for {
                     _ <- session.transactionScoped
                     _ <- for {
                            _ <- db.insert.one(Person("John", 42))
                            _ <- db.insert.one(Person("Jane", 43))
                          } yield ()
                     _ <- latch.await
                   } yield ())
                   .fork
        p1 <- query
        p2 <- latch.succeed(()) *> fiber.join *> query
      } yield assertTrue(p1.isEmpty, p2.size == 2)
    },
    test("simple - transactional mask") {
      for {
        client      <- ZIO.service[MongoClient]
        session     <- client.startSession
        db          <- Database.make("test")
        outerLatch1 <- Promise.make[Nothing, Unit]
        outerLatch2 <- Promise.make[Nothing, Unit]
        latch1      <- Promise.make[Nothing, Unit]
        latch2      <- Promise.make[Nothing, Unit]
        query        = db.findAll.stream[Person]().runCollect
        fiber <- session.transactionalMask { restore =>
                   for {
                     _ <- restore(db.insert.one(Person("John", 42))) // Insert John outside the transaction
                     _ <- outerLatch1.succeed(()) *> latch1.await
                     _ <- db.update.one(Person("John", 42), Setter(Person.SetAge(43)))
                     _ <- restore(db.insert.one(Person("Joan", 43)))
                     _ <- db.insert.one(Person("Jane", 44))
                     _ <- outerLatch2.succeed(()) *> latch2.await
                   } yield ()
                 }.fork
        p1 <- outerLatch1.await *> query
        _  <- latch1.succeed(())
        p2 <- outerLatch2.await *> query
        _  <- latch2.succeed(()) *> fiber.join
        p3 <- query
      } yield assertTrue(
        p1 == Chunk(Person("John", 42)),                                        // John's update isn't visible
        p2 == Chunk(Person("John", 42), Person("Joan", 43)),                    // Jane's insert isn't visible
        p3 == Chunk(Person("John", 43), Person("Joan", 43), Person("Jane", 44)) // All updates visible
      )
    },
    test("abort transaction on error") {
      for {
        client  <- ZIO.service[MongoClient]
        session <- client.startSession
        db      <- Database.make("test")
        r <- session.transactional {
               for {
                 _ <- db.insert.one(Person("John", 42))
                 _ <- db.insert.one(Person("Jane", 43))
                 _ <- ZIO.fail("Boom")
               } yield ()
             }.either
        p <- db.findAll.stream[Person]().runCollect
      } yield assertTrue(r.isLeft, p.isEmpty)
    },
    test("abort on fiber interrupt") {
      for {
        client  <- ZIO.service[MongoClient]
        session <- client.startSession
        db      <- Database.make("test")
        r <- session.transactional {
               for {
                 _ <- db.insert.one(Person("John", 42))
                 _ <- db.insert.one(Person("Jane", 43))
                 _ <- ZIO.never
               } yield ()
             }.fork
        _ <- r.interrupt
        p <- db.findAll.stream[Person]().runCollect
      } yield assertTrue(p.isEmpty)
    },
    test("apply transactional aspect") {
      for {
        client  <- ZIO.service[MongoClient]
        session <- client.startSession
        db      <- Database.make("test")
        latch   <- Promise.make[Nothing, Unit]
        query    = db.findAll.stream[Person]().runCollect
        fiber   <- ((db.insert.one(Person("John", 42)) *> latch.await *> query) @@ session.transactionally).fork
        p1      <- db.findAll.stream[Person]().runCollect
        p2      <- latch.succeed(()) *> fiber.join
      } yield assertTrue(p1.isEmpty, p2.size == 1)
    }
  ).provideSome[Scope with MongoClient](
    randomCollection
  ) @@ TestAspect.withLiveRandom @@ TestAspect.withLiveClock @@ TestAspect.sequential @@ TestAspect.tag("transactions")

}
