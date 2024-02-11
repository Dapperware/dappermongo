package dappermongo

import com.mongodb.reactivestreams.client.ClientSession
import dappermongo.Session.TransactionRestorer
import zio.{Exit, Scope, Trace, ZIO, ZIOAspect}

import dappermongo.internal.PublisherOps

trait Session {

  def transactionScoped: ZIO[Scope, Throwable, Unit]

  def transactional[R, E, A](zio: => ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.scoped[R](transactionScoped.orDie *> zio)

  def transactionalMask[R, E, A](k: TransactionRestorer => ZIO[R, E, A]): ZIO[R, E, A]

  def transactionally: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        transactional(zio)
    }

}

object Session {
  sealed trait TransactionRestorer {
    def apply[R, E, A](effect: => ZIO[R, E, A]): ZIO[R, E, A]
  }

  object TransactionRestorer {
    def apply(current: Option[ClientSession], sessionStorage: SessionStorage[ClientSession]): TransactionRestorer =
      new TransactionRestorer {
        override def apply[R, E, A](effect: => ZIO[R, E, A]): ZIO[R, E, A] =
          sessionStorage.locally(current)(effect)
      }
  }

  private[dappermongo] def apply(session: ClientSession, sessionStorage: SessionStorage[ClientSession]): Session =
    Impl(session, sessionStorage)

  private case class Impl(session: ClientSession, sessionStorage: SessionStorage[ClientSession]) extends Session {
    override def transactionScoped: ZIO[Scope, Throwable, Unit] =
      ZIO.acquireReleaseExit(
        ZIO.attempt(session.startTransaction())
      ) {
        case (_, Exit.Success(_)) => session.commitTransaction().empty.orDie
        case (_, Exit.Failure(_)) => session.abortTransaction().empty.orDie
      } <* sessionStorage.locallyScoped(Some(session))

    override def transactionalMask[R, E, A](k: TransactionRestorer => ZIO[R, E, A]): ZIO[R, E, A] =
      ZIO.scoped[R](for {
        current <- sessionStorage.get
        restorer = TransactionRestorer(current, sessionStorage)
        result  <- transactionScoped.orDie *> k(restorer)
      } yield result)
  }
}
