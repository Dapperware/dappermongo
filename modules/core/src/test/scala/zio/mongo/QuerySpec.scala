package zio.mongo

import zio._
import zio.bson.magnolia.{DeriveBsonDecoder, DeriveBsonEncoder}
import zio.bson.{BsonDecoder, BsonEncoder}
import zio.test.{TestAspect, assertTrue}

object QuerySpec extends MongoITSpecDefault {

  val spec = suite("Query")(
    test("insert and find") {
      case class Person(name: String, age: Int)

      implicit val encoder: BsonEncoder[Person] = DeriveBsonEncoder.derive[Person]
      implicit val decoder: BsonDecoder[Person] = DeriveBsonDecoder.derive[Person]

      for {
        client    <- ZIO.service[MongoClient]
        db        <- client.database("test")
        collection = db.collection("people")
        result <- collection(
                    db.insert.one(Person("John", 42)) *>
                      db.find(Person("John", 42)).one[Person]
                  )
      } yield assertTrue(result.get == Person("John", 42))
    },
    test("insert, delete and find") {
      case class Person(name: String, age: Int)

      implicit val encoder: BsonEncoder[Person] = DeriveBsonEncoder.derive[Person]
      implicit val decoder: BsonDecoder[Person] = DeriveBsonDecoder.derive[Person]

      for {
        client    <- ZIO.service[MongoClient]
        db        <- client.database("test")
        collection = db.collection("people2")
        result <- collection(
                    db.insert.one(Person("John", 42)) *>
                      db.delete.one(Person("John", 42)) *>
                      db.find(Person("John", 42)).one[Person]
                  )
      } yield assertTrue(result.isEmpty)
    }
  ) @@ TestAspect.sequential

}
