package dappermongo

import zio.{FiberRef, Scope, UIO, URIO, ZIO}

private[dappermongo] trait SessionStorage[Session] {

  def get: UIO[Option[Session]]

  def locallyScoped(value: Option[Session]): URIO[Scope, Unit]

  def locally[R, E, B](newValue: Option[Session])(zio: ZIO[R, E, B]): ZIO[R, E, B]

}

object SessionStorage {

  def fiberRef[Session]: ZIO[Scope, Nothing, SessionStorage[Session]] =
    for {
      ref <- FiberRef.make[Option[Session]](None)
    } yield new SessionStorage[Session] {
      override def get: UIO[Option[Session]] =
        ref.get

      override def locallyScoped(value: Option[Session]): URIO[Scope, Unit] =
        ref.locallyScoped(value)

      override def locally[R, E, B](newValue: Option[Session])(zio: ZIO[R, E, B]): ZIO[R, E, B] =
        ref.locally(newValue)(zio)
    }

}
