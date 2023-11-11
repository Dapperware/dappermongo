package zio.mongo

import zio.ZIO
import zio.stream.ZStream

trait Collection {
  def name: String

  def apply[R, E, A](zio: ZIO[Collection with R, E, A]): ZIO[R, E, A] =
    zio.provideSomeEnvironment[R](_.add[Collection](this))

  def apply[R, E, A](stream: ZStream[Collection with R, E, A]): ZStream[R, E, A] =
    stream.provideSomeEnvironment[R](_.add[Collection](this))
}

object Collection {

  def apply(name: String): Collection =
    Impl(name)

  private[mongo] case class Impl(name: String) extends Collection
}
