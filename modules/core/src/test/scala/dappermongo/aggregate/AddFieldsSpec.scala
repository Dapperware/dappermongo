package dappermongo.aggregate

import dappermongo.{Database, MongoClient, MongoITSpecDefault}
import reactivemongo.api.bson.{BSONDocumentReader, Macros, array, document}
import zio.test.assertTrue
import zio.{Chunk, ZIO}

object AddFieldsSpec extends MongoITSpecDefault {

  case class StudentScore(
    _id: Int,
    student: String,
    homework: List[Int],
    quiz: List[Int],
    extraCredit: Int,
    totalHomework: Int,
    totalQuiz: Int,
    totalScore: Int
  )

  implicit val studentScoreReader: BSONDocumentReader[StudentScore] = Macros.reader[StudentScore]

  case class Specs(doors: Option[Int], wheels: Option[Int], fuel_type: Option[String])

  implicit val specsReader: BSONDocumentReader[Specs] = Macros.reader[Specs]
  case class Vehicle(_id: Int, `type`: String, specs: Option[Specs])

  implicit val vehicleReader: BSONDocumentReader[Vehicle] = Macros.reader[Vehicle]

  case class Animal(_id: Int, dogs: Int, cats: Int)
  implicit val animalReader: BSONDocumentReader[Animal] = Macros.reader[Animal]

  case class Fruit(_id: String, item: String, `type`: String)
  implicit val fruitReader: BSONDocumentReader[Fruit] = Macros.reader[Fruit]

  case class Score(_id: Int, student: String, homework: List[Int], quiz: List[Int], extraCredit: Int)
  implicit val scoreReader: BSONDocumentReader[Score] = Macros.reader[Score]

  val spec = suite("Aggregate - $addFields")(
    test("Using Two $addFields stages") {
      val scores = List(
        document(
          "_id"         -> 1,
          "student"     -> "Maya",
          "homework"    -> List(10, 5, 10),
          "quiz"        -> List(10, 8),
          "extraCredit" -> 0
        ),
        document("_id" -> 2, "student" -> "Ryan", "homework" -> List(5, 6, 5), "quiz" -> List(8, 8), "extraCredit" -> 8)
      )

      val pipeline = Stage.addFields(
        document(
          "totalHomework" -> document("$sum" -> "$homework"),
          "totalQuiz"     -> document("$sum" -> "$quiz")
        )
      ) >>> Stage.addFields(
        document(
          "totalScore" -> document("$add" -> List("$totalHomework", "$totalQuiz", "$extraCredit"))
        )
      )

      for {
        db        <- ZIO.service[Database]
        collection = db.collection("scores")
        _         <- collection(db.insert.many(scores))
        result    <- collection(db.aggregate(pipeline).stream[StudentScore].runCollect)
      } yield assertTrue(
        result == Chunk(
          StudentScore(
            _id = 1,
            student = "Maya",
            homework = List(10, 5, 10),
            quiz = List(10, 8),
            extraCredit = 0,
            totalHomework = 25,
            totalQuiz = 18,
            totalScore = 43
          ),
          StudentScore(
            _id = 2,
            student = "Ryan",
            homework = List(5, 6, 5),
            quiz = List(8, 8),
            extraCredit = 8,
            totalHomework = 16,
            totalQuiz = 16,
            totalScore = 40
          )
        )
      )
    },
    test("Adding Fields to an Embedded Document") {
      val vehicles = List(
        document("_id" -> 1, "type" -> "car", "specs"        -> document("doors" -> 4, "wheels" -> 4)),
        document("_id" -> 2, "type" -> "motorcycle", "specs" -> document("doors" -> 0, "wheels" -> 2)),
        document("_id" -> 3, "type" -> "jet ski")
      )

      val pipeline = Stage.addFields(
        document("specs.fuel_type" -> "unleaded")
      )

      for {
        db        <- ZIO.service[Database]
        collection = db.collection("vehicles")
        _         <- collection(db.insert.many(vehicles))
        result    <- collection(db.aggregate(pipeline).stream[Vehicle].runCollect)
      } yield assertTrue(
        result == Chunk(
          Vehicle(
            _id = 1,
            `type` = "car",
            specs = Some(Specs(doors = Some(4), wheels = Some(4), fuel_type = Some("unleaded")))
          ),
          Vehicle(
            _id = 2,
            `type` = "motorcycle",
            specs = Some(Specs(doors = Some(0), wheels = Some(2), fuel_type = Some("unleaded")))
          ),
          Vehicle(
            _id = 3,
            `type` = "jet ski",
            specs = Some(Specs(doors = None, wheels = None, fuel_type = Some("unleaded")))
          )
        )
      )
    },
    test("Overwriting an existing field") {
      val animals = List(
        document("_id" -> 1, "dogs" -> 10, "cats" -> 15)
      )

      val pipeline = Stage.addFields(
        document("cats" -> 20)
      )

      for {
        db        <- ZIO.service[Database]
        collection = db.collection("animals")
        _         <- collection(db.insert.many(animals))
        result    <- collection(db.aggregate(pipeline).stream[Animal].runCollect)
      } yield assertTrue(
        result == Chunk(
          Animal(_id = 1, dogs = 10, cats = 20)
        )
      )
    },
    test("Replacing one field with another") {
      val fruits = List(
        document("_id" -> 1, "item" -> "tangerine", "type"  -> "citrus"),
        document("_id" -> 2, "item" -> "lemon", "type"      -> "citrus"),
        document("_id" -> 3, "item" -> "grapefruit", "type" -> "citrus")
      )

      val pipeline = Stage.addFields(
        document(
          "_id"  -> "$item",
          "item" -> "fruit"
        )
      )

      for {
        db        <- ZIO.service[Database]
        collection = db.collection("fruit")
        _         <- collection(db.insert.many(fruits))
        result    <- collection(db.aggregate(pipeline).stream[Fruit].runCollect)
      } yield assertTrue(
        result == Chunk(
          Fruit(_id = "tangerine", item = "fruit", `type` = "citrus"),
          Fruit(_id = "lemon", item = "fruit", `type` = "citrus"),
          Fruit(_id = "grapefruit", item = "fruit", `type` = "citrus")
        )
      )
    },
    test("Add Element to an Array") {
      val scores = List(
        document(
          "_id"         -> 1,
          "student"     -> "Maya",
          "homework"    -> List(10, 5, 10),
          "quiz"        -> List(10, 8),
          "extraCredit" -> 0
        ),
        document(
          "_id"         -> 2,
          "student"     -> "Ryan",
          "homework"    -> List(5, 6, 5),
          "quiz"        -> List(8, 8),
          "extraCredit" -> 8
        )
      )

      val pipeline = Stage.`match`(document("_id" -> 1)) >>> Stage.addFields(
        document("homework" -> document("$concatArrays" -> array("$homework", array(7))))
      )

      for {
        db        <- ZIO.service[Database]
        collection = db.collection("scores_2")
        _         <- collection(db.insert.many(scores))
        result    <- collection(db.aggregate(pipeline).stream[Score].runCollect)
      } yield assertTrue(
        result == Chunk(
          Score(_id = 1, student = "Maya", homework = List(10, 5, 10, 7), quiz = List(10, 8), extraCredit = 0)
        )
      )
    }
  ).provideSomeShared[MongoClient](
    database("aggregates")
  )

}
