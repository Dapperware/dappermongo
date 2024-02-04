package dappermongo.aggregate

import dappermongo.{Database, MongoClient, MongoITSpecDefault}
import reactivemongo.api.bson.{BSONDocumentReader, BSONInteger, BSONString, BSONValue, Macros, array, document}
import zio.ZIO
import zio.test.assertTrue

object FacetSpec extends MongoITSpecDefault {

  case class YearBucket(_id: Option[MinMax], count: Int)
  case class MinMax(min: Option[Int], max: Option[Int])
  case class PriceBucket(_id: BSONValue, count: Int, titles: List[String])
  case class TagBucket(_id: String, count: Int)
  case class FacetResult(
    categorizedByYearsAuto: List[YearBucket],
    categorizedByPrice: List[PriceBucket],
    categorizedByTags: List[TagBucket]
  )

  implicit val priceBucketReader: BSONDocumentReader[PriceBucket] = Macros.reader[PriceBucket]
  implicit val minMaxReader: BSONDocumentReader[MinMax]           = Macros.reader[MinMax]
  implicit val tagBucketReader: BSONDocumentReader[TagBucket]     = Macros.reader[TagBucket]
  implicit val yearBucketReader: BSONDocumentReader[YearBucket]   = Macros.reader[YearBucket]
  implicit val facetResultReader: BSONDocumentReader[FacetResult] = Macros.reader[FacetResult]

  val artwork = List(
    document(
      "_id"    -> 1,
      "title"  -> "The Pillars of Society",
      "artist" -> "Grosz",
      "year"   -> 1926,
      "price"  -> 199.99,
      "tags"   -> array("painting", "satire", "Expressionism", "caricature")
    ),
    document(
      "_id"    -> 2,
      "title"  -> "Melancholy III",
      "artist" -> "Munch",
      "year"   -> 1902,
      "price"  -> 280.00,
      "tags"   -> array("woodcut", "Expressionism")
    ),
    document(
      "_id"    -> 3,
      "title"  -> "Dancer",
      "artist" -> "Miro",
      "year"   -> 1925,
      "price"  -> 76.04,
      "tags"   -> array("oil", "Surrealism", "painting")
    ),
    document(
      "_id"    -> 4,
      "title"  -> "The Great Wave off Kanagawa",
      "artist" -> "Hokusai",
      "price"  -> 167.30,
      "tags"   -> array("woodblock", "ukiyo-e")
    ),
    document(
      "_id"    -> 5,
      "title"  -> "The Persistence of Memory",
      "artist" -> "Dali",
      "year"   -> 1931,
      "price"  -> 483.00,
      "tags"   -> array("Surrealism", "painting", "oil")
    ),
    document(
      "_id"    -> 6,
      "title"  -> "Composition VII",
      "artist" -> "Kandinsky",
      "year"   -> 1913,
      "price"  -> 385.00,
      "tags"   -> array("oil", "painting", "abstract")
    ),
    document(
      "_id"    -> 7,
      "title"  -> "The Scream",
      "artist" -> "Munch",
      "year"   -> 1893,
      "tags"   -> array("Expressionism", "painting", "oil")
    ),
    document(
      "_id"    -> 8,
      "title"  -> "Blue Flower",
      "artist" -> "O'Keefe",
      "year"   -> 1918,
      "price"  -> 118.42,
      "tags"   -> array("abstract", "painting")
    )
  )

  val spec = suite("Aggregate - $facet")(
    test("Categorize by Tags") {
      val pipeline = Stage.facet(
        "categorizedByTags" -> (Stage.unwind("$tags") >>> Stage.sortByCount("$tags")),
        "categorizedByPrice" -> (Stage.`match`(document("price" -> document("$exists" -> true))) >>>
          Stage.bucket(
            "$price",
            boundaries = array(0, 150, 200, 300, 400),
            default = Some(BSONString("Other")),
            output = document("count" -> document("$sum" -> 1), "titles" -> document("$push" -> "$title"))
          )),
        "categorizedByYearsAuto" -> Stage.bucketAuto(
          "$year",
          buckets = 4
        )
      )

      val expected = FacetResult(
        categorizedByYearsAuto = List(
          YearBucket(Some(MinMax(None, Some(1902))), 2),
          YearBucket(Some(MinMax(Some(1902), Some(1918))), 2),
          YearBucket(Some(MinMax(Some(1918), Some(1926))), 2),
          YearBucket(Some(MinMax(Some(1926), Some(1931))), 2)
        ),
        categorizedByPrice = List(
          PriceBucket(BSONInteger(0), 2, List("Dancer", "Blue Flower")),
          PriceBucket(BSONInteger(150), 2, List("The Pillars of Society", "The Great Wave off Kanagawa")),
          PriceBucket(BSONInteger(200), 1, List("Melancholy III")),
          PriceBucket(BSONInteger(300), 1, List("Composition VII")),
          PriceBucket(BSONString("Other"), 1, List("The Persistence of Memory"))
        ),
        categorizedByTags = List(
          TagBucket("painting", 6),
          TagBucket("oil", 4),
          TagBucket("Expressionism", 3),
          TagBucket("Surrealism", 2),
          TagBucket("abstract", 2),
          TagBucket("woodblock", 1),
          TagBucket("woodcut", 1),
          TagBucket("ukiyo-e", 1),
          TagBucket("satire", 1),
          TagBucket("caricature", 1)
        ).sortBy(tb => tb.count -> tb._id)
      )

      for {
        db                    <- ZIO.service[Database]
        collection             = db.collection("facets")
        _                     <- collection(db.insert.many(artwork))
        result                <- collection(db.aggregate(pipeline).one[FacetResult])
        categorizedByYearsAuto = result.map(_.categorizedByYearsAuto)
        categorizedByPrice     = result.map(_.categorizedByPrice)
        categorizedByTags      = result.map(_.categorizedByTags)
      } yield assertTrue(
        categorizedByYearsAuto.get == expected.categorizedByYearsAuto &&
          categorizedByPrice.get == expected.categorizedByPrice &&
          categorizedByTags.get.sortBy(tb => tb.count -> tb._id) == expected.categorizedByTags
      )
    }
  ).provideSomeShared[MongoClient](
    database("aggregates")
  )

}
