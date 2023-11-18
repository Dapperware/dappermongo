package zio.mongo

import zio.Chunk
import zio.test.{TestAspect, TestAspectAtLeastR, TestEnvironment, ZIOSpec}

import zio.durationInt

abstract class MongoITSpecDefault extends ZIOSpec[MongoClient] {

  val bootstrap =
    Container.live >>> Container.settings >>> MongoClient.live

  override val aspects: Chunk[TestAspectAtLeastR[MongoClient with TestEnvironment]] =
    super.aspects ++ List(TestAspect.timeout(60.seconds))
}
