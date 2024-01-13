package dappermongo

import zio.bson.{BsonCodec, bsonField}
import zio.schema.codec.BsonSchemaCodec
import zio.schema.{DeriveSchema, Schema}
import zio.test._
import zio.{Chunk, Random, Scope, ZIO, ZLayer}

object TransactionSpec extends MongoITSpecDefault {

  private val randomCollection =
    ZLayer(Random.nextIntBetween(0, Int.MaxValue).map(value => Collection(s"test-$value")))

  case class Setter[A](@bsonField("$set") value: A)

  object Setter {
    def apply[A](value: A): Setter[A] = new Setter(value)
    implicit def codec[A: Schema]: BsonCodec[Setter[A]] = {
      val _ = implicitly[Schema[A]] // To satisfy scalafix
      BsonSchemaCodec.bsonCodec(DeriveSchema.gen[Setter[A]])
    }
  }

  case class Person(@bsonField("_id") name: String, age: Int)

  object Person {
    val schema = DeriveSchema.gen[Person]

    implicit val codec: BsonCodec[Person] = BsonSchemaCodec.bsonCodec(schema)
    case class SetAge(age: Int)

    object SetAge {
      implicit val schema: Schema[SetAge] = DeriveSchema.gen[SetAge]
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
      } yield assertTrue(p1.isEmpty, p2.size == 2)
    },
    test("simple - scoped") {

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
    },
    test("simple - transactional mask") {
      for {
        client  <- ZIO.service[MongoClient]
        session <- client.startSession
        db      <- Database.make("test")
        r <- session.transactionalMask { restore =>
               for {
                 _ <- restore(db.insert.one(Person("John", 42))) // Insert John outside the transaction
                 _  <- db.update.one(Person("John", 42), Setter(Person.SetAge(43)))
                 _  <- restore(db.insert.one(Person("Joan", 43)))
                 p1 <- db.findAll.stream[Person]().runCollect
                 _  <- db.insert.one(Person("Jane", 44))
                 p2 <- db.findAll.stream[Person]().runCollect
               } yield (p1, p2)
             }
        (p1, p2) = r
        p3      <- db.findAll.stream[Person]().runCollect
      } yield assertTrue(
        p1 == Chunk(Person("John", 42), Person("Joan", 43)),                    // John's update isn't visible
        p2 == Chunk(Person("John", 42), Person("Joan", 43)),                    // Jane's insert isn't visible
        p3 == Chunk(Person("John", 43), Person("Joan", 43), Person("Jane", 44)) // All updates visible
      )
    }
  ).provideSome[Scope with MongoClient](
    randomCollection
  ) @@ TestAspect.withLiveRandom @@ TestAspect.withLiveClock @@ TestAspect.sequential

}
