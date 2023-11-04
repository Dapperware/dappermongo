package zio.mongo

import zio.ZIO
import zio.stream.ZStream

trait Collection {
  def name: String

  def apply[R, E, A](zio: ZIO[Collection with R, E, A]): ZIO[R, E, A] = ???

  def apply[R, E, A](stream: ZStream[Collection with R, E, A]): ZStream[R, E, A] = ???
}

private[mongo] case class CollectionImpl(name: String) extends Collection
