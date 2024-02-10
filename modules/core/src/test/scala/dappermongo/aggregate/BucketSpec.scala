package dappermongo.aggregate

import dappermongo.{Database, MongoITSpecDefault}
import reactivemongo.api.bson.{BSONDocumentReader, BSONInteger, BSONString, BSONValue, Macros, array, document}
import zio.ZIO
import zio.test.assertTrue

object BucketSpec extends MongoITSpecDefault {

  case class Artist(
    _id: Int,
    last_name: String,
    first_name: String,
    year_born: Int,
    year_died: Int,
    nationality: String
  )

  implicit val artistReader: BSONDocumentReader[Artist] = Macros.reader[Artist]

  case class Artwork(
    _id: Int,
    title: String,
    artist: String,
    year: Int,
    price: Double
  )

  implicit val artworkReader: BSONDocumentReader[Artwork] = Macros.reader[Artwork]

  case class ArtworkInfo(title: String, price: Option[Double], year: Option[Int])

  case class BucketInfo(_id: BSONValue, count: Int, artwork: List[ArtworkInfo], averagePrice: Option[BigDecimal])

  case class PriceAndArtworkResponse(price: List[BucketInfo], year: List[BucketInfo])

  case class ArtistInfo(name: String, year_born: Int)
  case class BucketResult(_id: Int, count: Int, artists: List[ArtistInfo])

  implicit val bucketInfoReader: BSONDocumentReader[BucketInfo]            = Macros.reader[BucketInfo]
  implicit val artworkInfoReader: BSONDocumentReader[ArtworkInfo]          = Macros.reader[ArtworkInfo]
  implicit val responseReader: BSONDocumentReader[PriceAndArtworkResponse] = Macros.reader[PriceAndArtworkResponse]

  implicit val artistInfoWriter: BSONDocumentReader[ArtistInfo]     = Macros.reader[ArtistInfo]
  implicit val bucketResultReader: BSONDocumentReader[BucketResult] = Macros.reader[BucketResult]

  val artwork = List(
    document("_id" -> 1, "title" -> "The Pillars of Society", "artist"      -> "Grosz", "year"     -> 1926, "price" -> 199.99),
    document("_id" -> 2, "title" -> "Melancholy III", "artist"              -> "Munch", "year"     -> 1902, "price" -> 280.00),
    document("_id" -> 3, "title" -> "Dancer", "artist"                      -> "Miro", "year"      -> 1925, "price" -> 76.04),
    document("_id" -> 4, "title" -> "The Great Wave off Kanagawa", "artist" -> "Hokusai", "price"  -> 167.30),
    document("_id" -> 5, "title" -> "The Persistence of Memory", "artist"   -> "Dali", "year"      -> 1931, "price" -> 483.00),
    document("_id" -> 6, "title" -> "Composition VII", "artist"             -> "Kandinsky", "year" -> 1913, "price" -> 385.00),
    document("_id" -> 7, "title" -> "The Scream", "artist"                  -> "Munch", "year"     -> 1893),
    document("_id" -> 8, "title" -> "Blue Flower", "artist"                 -> "O'Keefe", "year"   -> 1918, "price" -> 118.42)
  )

  val artists = List(
    document(
      "_id"         -> 1,
      "last_name"   -> "Bernard",
      "first_name"  -> "Emil",
      "year_born"   -> 1868,
      "year_died"   -> 1941,
      "nationality" -> "France"
    ),
    document(
      "_id"         -> 2,
      "last_name"   -> "Rippl-Ronai",
      "first_name"  -> "Joszef",
      "year_born"   -> 1861,
      "year_died"   -> 1927,
      "nationality" -> "Hungary"
    ),
    document(
      "_id"         -> 3,
      "last_name"   -> "Ostroumova",
      "first_name"  -> "Anna",
      "year_born"   -> 1871,
      "year_died"   -> 1955,
      "nationality" -> "Russia"
    ),
    document(
      "_id"         -> 4,
      "last_name"   -> "Van Gogh",
      "first_name"  -> "Vincent",
      "year_born"   -> 1853,
      "year_died"   -> 1890,
      "nationality" -> "Holland"
    ),
    document(
      "_id"         -> 5,
      "last_name"   -> "Maurer",
      "first_name"  -> "Alfred",
      "year_born"   -> 1868,
      "year_died"   -> 1932,
      "nationality" -> "USA"
    ),
    document(
      "_id"         -> 6,
      "last_name"   -> "Munch",
      "first_name"  -> "Edvard",
      "year_born"   -> 1863,
      "year_died"   -> 1944,
      "nationality" -> "Norway"
    ),
    document(
      "_id"         -> 7,
      "last_name"   -> "Redon",
      "first_name"  -> "Odilon",
      "year_born"   -> 1840,
      "year_died"   -> 1916,
      "nationality" -> "France"
    ),
    document(
      "_id"         -> 8,
      "last_name"   -> "Diriks",
      "first_name"  -> "Edvard",
      "year_born"   -> 1855,
      "year_died"   -> 1930,
      "nationality" -> "Norway"
    )
  )

  val spec = suite("Aggregate - $bucket")(
    test("Bucket by Year and Filter by Bucket Results") {
      val pipeline = Stage.bucket(
        groupBy = "$year_born",
        boundaries = array(1840, 1850, 1860, 1870, 1880),
        default = Some(BSONString("Other")),
        output = document(
          "count" -> document("$sum" -> 1),
          "artists" -> document(
            "$push" -> document(
              "name"      -> document("$concat" -> array("$first_name", " ", "$last_name")),
              "year_born" -> "$year_born"
            )
          )
        )
      ) >>> Stage.`match`(document("count" -> document("$gt" -> 3)))

      val expected =
        BucketResult(
          _id = 1860,
          count = 4,
          artists = List(
            ArtistInfo(name = "Emil Bernard", year_born = 1868),
            ArtistInfo(name = "Joszef Rippl-Ronai", year_born = 1861),
            ArtistInfo(name = "Alfred Maurer", year_born = 1868),
            ArtistInfo(name = "Edvard Munch", year_born = 1863)
          )
        )

      for {
        db         <- ZIO.service[Database]
        collection <- newCollection("artists")
        _          <- collection(db.insert.many(artists))
        result     <- collection(db.aggregate(pipeline).one[BucketResult])
      } yield assertTrue(result.get == expected)
    },
    test("Use $bucket with $facet to Bucket by Multiple Fields") {
      val pipeline = Stage.facet(
        "price" ->
          Stage.bucket(
            groupBy = "$price",
            boundaries = array(0, 200, 400),
            default = Some(BSONString("Other")),
            output = document(
              "count"        -> document("$sum" -> 1),
              "artwork"      -> document("$push" -> document("title" -> "$title", "price" -> "$price")),
              "averagePrice" -> document("$avg" -> "$price")
            )
          ),
        "year" ->
          Stage.bucket(
            groupBy = "$year",
            boundaries = array(1890, 1910, 1920, 1940),
            default = Some(BSONString("Unknown")),
            output = document(
              "count"   -> document("$sum" -> 1),
              "artwork" -> document("$push" -> document("title" -> "$title", "year" -> "$year"))
            )
          )
      )

      val response = PriceAndArtworkResponse(
        price = List(
          BucketInfo(
            _id = BSONInteger(0),
            count = 4,
            artwork = List(
              ArtworkInfo(title = "The Pillars of Society", price = Some(199.99), year = None),
              ArtworkInfo(title = "Dancer", price = Some(76.04), year = None),
              ArtworkInfo(title = "The Great Wave off Kanagawa", price = Some(167.30), year = None),
              ArtworkInfo(title = "Blue Flower", price = Some(118.42), year = None)
            ),
            averagePrice = Some(BigDecimal("140.4375"))
          ),
          BucketInfo(
            _id = BSONInteger(200),
            count = 2,
            artwork = List(
              ArtworkInfo(title = "Melancholy III", price = Some(280.00), year = None),
              ArtworkInfo(title = "Composition VII", price = Some(385.00), year = None)
            ),
            averagePrice = Some(BigDecimal("332.50"))
          ),
          BucketInfo(
            _id = BSONString("Other"),
            count = 2,
            artwork = List(
              ArtworkInfo(title = "The Persistence of Memory", price = Some(483.00), year = None),
              ArtworkInfo(title = "The Scream", price = None, year = None)
            ),
            averagePrice = Some(BigDecimal("483.00"))
          )
        ),
        year = List(
          BucketInfo(
            _id = BSONInteger(1890),
            count = 2,
            artwork = List(
              ArtworkInfo(title = "Melancholy III", price = None, year = Some(1902)),
              ArtworkInfo(title = "The Scream", price = None, year = Some(1893))
            ),
            averagePrice = None
          ),
          BucketInfo(
            _id = BSONInteger(1910),
            count = 2,
            artwork = List(
              ArtworkInfo(title = "Composition VII", price = None, year = Some(1913)),
              ArtworkInfo(title = "Blue Flower", price = None, year = Some(1918))
            ),
            averagePrice = None
          ),
          BucketInfo(
            _id = BSONInteger(1920),
            count = 3,
            artwork = List(
              ArtworkInfo(title = "The Pillars of Society", price = None, year = Some(1926)),
              ArtworkInfo(title = "Dancer", price = None, year = Some(1925)),
              ArtworkInfo(title = "The Persistence of Memory", price = None, year = Some(1931))
            ),
            averagePrice = None
          ),
          BucketInfo(
            _id = BSONString("Unknown"),
            count = 1,
            artwork = List(
              ArtworkInfo(title = "The Great Wave off Kanagawa", price = None, year = None)
            ),
            averagePrice = None
          )
        )
      )

      for {
        db         <- ZIO.service[Database]
        collection <- newCollection("artwork")
        _          <- collection(db.insert.many(artwork))
        result     <- collection(db.aggregate(pipeline).one[PriceAndArtworkResponse])
      } yield assertTrue(
        result.get == response
      )
    }
  ).provideSomeShared[Env](
    database("aggregates")
  )

}
