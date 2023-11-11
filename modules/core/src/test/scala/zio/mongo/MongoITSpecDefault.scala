package zio.mongo

import zio.test.ZIOSpec

abstract class MongoITSpecDefault extends ZIOSpec[MongoClient] {

  val bootstrap =
    Container.live >>> Container.settings >>> MongoClient.live
}
