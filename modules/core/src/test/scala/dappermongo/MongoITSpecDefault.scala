package dappermongo

import dappermongo.MongoITSpecDefault.layer
import zio._
import zio.test.{TestAspect, TestAspectAtLeastR, TestEnvironment, ZIOSpec}

abstract class MongoITSpecDefault extends ZIOSpec[MongoClient with CollectionProvider] {
  type Env = MongoClient with CollectionProvider

  val bootstrap = layer

  override val aspects: Chunk[TestAspectAtLeastR[Environment with TestEnvironment]] =
    super.aspects ++ List(TestAspect.timeout(60.seconds))

  def database(name: String): ZLayer[MongoClient, Throwable, Database] =
    ZLayer(ZIO.serviceWithZIO[MongoClient](_.database(name)))

  def newCollection(name: String): ZIO[CollectionProvider, Throwable, Collection] =
    ZIO.serviceWithZIO[CollectionProvider](_.collection(name))
}

object MongoITSpecDefault {
  val layer = Container.live >>> MongoClient.live ++ CollectionProvider.live
}
