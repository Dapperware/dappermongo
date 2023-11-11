package zio.mongo

import zio._
import zio.bson.BsonCodec
import zio.schema.codec.BsonSchemaCodec
import zio.schema.{DeriveSchema, Schema}
import zio.test.{TestAspect, assertTrue}

object QuerySpec extends MongoITSpecDefault {
  case class Person(name: String, age: Int)

  object Person {
    val schema: Schema[Person]            = DeriveSchema.gen[Person]
    implicit val codec: BsonCodec[Person] = BsonSchemaCodec.bsonCodec(schema)
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
