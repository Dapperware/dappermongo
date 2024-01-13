package dappermongo

import zio.ZIO
import zio.stream.ZStream

trait Collection { self =>
  def name: String

  def readPreference: Option[ReadPreference]

  def writeConcern: Option[WriteConcern]

  def readConcern: Option[ReadConcern]

  def writeConcern(wc: WriteConcern): Collection =
    Collection(name, readPreference, Some(wc))

  def readPreference(rp: ReadPreference): Collection =
    Collection(name, Some(rp), writeConcern)

  def readConcern(rc: ReadConcern): Collection =
    Collection(name, readPreference, writeConcern, Some(rc))

  def apply[R, E, A](zio: ZIO[Collection with R, E, A]): ZIO[R, E, A] =
    zio.provideSomeEnvironment[R](_.add[Collection](self))

  def apply[R, E, A](stream: ZStream[Collection with R, E, A]): ZStream[R, E, A] =
    stream.provideSomeEnvironment[R](_.add[Collection](self))
}

object Collection {

  def apply(
    name: String,
    readPreference: Option[ReadPreference] = None,
    writeConcern: Option[WriteConcern] = None,
    readConcern: Option[ReadConcern] = None
  ): Collection =
    Impl(name, readPreference, writeConcern, readConcern)

  private case class Impl(
    name: String,
    readPreference: Option[ReadPreference],
    writeConcern: Option[WriteConcern],
    readConcern: Option[ReadConcern]
  ) extends Collection
}
