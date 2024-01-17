package dappermongo

import reactivemongo.api.bson.{BSONDocumentHandler, Macros}
import zio._
import zio.test.{TestAspect, assertTrue}

object QuerySpec extends MongoITSpecDefault {
  case class Person(name: String, age: Int)

  object Person {
    implicit val codec: BSONDocumentHandler[Person] = Macros.handler[Person]
  }

  val spec = suite("Query")(
    test("insert and find") {

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
