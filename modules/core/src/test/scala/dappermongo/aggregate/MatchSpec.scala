import dappermongo.{Database, MongoClient, MongoITSpecDefault}
import dappermongo.aggregate.Stage
import reactivemongo.api.bson.{BSONDocument, BSONDocumentReader, BSONNull, Macros, array, document}
import zio.{Chunk, ZIO}
import zio.test._

object MatchSpec extends MongoITSpecDefault {

  case class Article(_id: String, author: String, score: Int, views: Int)
  implicit val articleReader: BSONDocumentReader[Article] = Macros.reader[Article]

  val articles = List(
    document("_id" -> "512bc95fe835e68f199c8686", "author" -> "dave", "score" -> 80, "views" -> 100),
    document("_id" -> "512bc962e835e68f199c8687", "author" -> "dave", "score" -> 85, "views" -> 521),
    document("_id" -> "55f5a192d4bede9ac365b257", "author" -> "ahn", "score"  -> 60, "views" -> 1000),
    document("_id" -> "55f5a192d4bede9ac365b258", "author" -> "li", "score"   -> 55, "views" -> 5000),
    document("_id" -> "55f5a1d3d4bede9ac365b259", "author" -> "annT", "score" -> 60, "views" -> 50),
    document("_id" -> "55f5a1d3d4bede9ac365b25a", "author" -> "li", "score"   -> 94, "views" -> 999),
    document("_id" -> "55f5a1d3d4bede9ac365b25b", "author" -> "ty", "score"   -> 95, "views" -> 1000)
  )

  val spec = suite("Aggregate - $match")(
    test("Equality Match") {

      val pipeline = Stage.`match`(document("author" -> "dave"))

      for {
        db        <- ZIO.service[Database]
        collection = db.collection("articles")
        _         <- collection(db.insert.many(articles))
        result    <- collection(db.aggregate(pipeline).stream[Article].runCollect)
      } yield assertTrue(
        result == Chunk(
          Article(_id = "512bc95fe835e68f199c8686", author = "dave", score = 80, views = 100),
          Article(_id = "512bc962e835e68f199c8687", author = "dave", score = 85, views = 521)
        )
      )
    },
    test("Perform a Count") {
      val pipeline = Stage.`match`(
        document(
          "$or" -> array(
            document("score" -> document("$gt" -> 70, "$lt" -> 90)),
            document("views" -> document("$gte" -> 1000))
          )
        )
      ) >>> Stage.group(document("_id" -> BSONNull), document("count" -> document("$sum" -> 1)))

      for {
        db        <- ZIO.service[Database]
        collection = db.collection("articles_2")
        _         <- collection(db.insert.many(articles))
        result    <- collection(db.aggregate(pipeline).one[BSONDocument])
      } yield assertTrue(result.get.getAsOpt[Int]("count").contains(5))
    }
  ).provideSomeShared[MongoClient](
    database("aggregates")
  )
}
