package dappermongo

import zio.{Random, ULayer, ZIO, ZLayer}

trait CollectionProvider {
  def collection: ZIO[Any, Throwable, Collection] = collection("test_collection")

  def collection(name: String): ZIO[Any, Throwable, Collection]
}

object CollectionProvider {
  val live: ULayer[CollectionProvider] = ZLayer.succeed(new CollectionProvider {
    override def collection(name: String): ZIO[Any, Throwable, Collection] =
      Random
        .nextIntBetween(1, Int.MaxValue)
        .map(suffix => Collection(s"${name}_$suffix"))
  })
}
