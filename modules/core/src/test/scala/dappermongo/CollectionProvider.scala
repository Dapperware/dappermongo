package dappermongo

import zio.{ULayer, ZIO, ZLayer}

trait CollectionProvider {
  def collection: ZIO[Any, Throwable, Collection] = collection("test_collection")

  def collection(name: String): ZIO[Any, Throwable, Collection]
}

object CollectionProvider {
  val live: ULayer[CollectionProvider] = ZLayer(for {
    random <- ZIO.random
  } yield new CollectionProvider {
    override def collection(name: String): ZIO[Any, Throwable, Collection] =
      random
        .nextIntBetween(1, Int.MaxValue)
        .map(suffix => Collection(s"${name}_$suffix"))
  })
}
