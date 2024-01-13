package zio.mongo

import com.mongodb.reactivestreams.client.ClientSession
import zio.mongo.Session.TransactionRestorer
import zio.{Exit, Scope, ZIO}

import zio.mongo.internal.PublisherOps

trait Session {

  def transactionScoped: ZIO[Scope, Throwable, Unit]

  def transactional[R, E, A](zio: => ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.scoped[R](transactionScoped.orDie *> zio)

  def transactionalMask[R, E, A](k: TransactionRestorer => ZIO[R, E, A]): ZIO[R, E, A]

}

object Session {
  sealed trait TransactionRestorer {
    def apply[R, E, A](effect: => ZIO[R, E, A]): ZIO[R, E, A]
  }

  object TransactionRestorer {
    def apply(current: Option[ClientSession]): TransactionRestorer =
      new TransactionRestorer {
        override def apply[R, E, A](effect: => ZIO[R, E, A]): ZIO[R, E, A] =
          MongoClient.stateRef.locallyWith(_.copy(session = current))(effect)
      }
  }

  def make(session: ClientSession): ZIO[Scope, Nothing, Session] =
    for {
      _ <- MongoClient.stateRef.locallyScopedWith(_.copy(session = Some(session)))
    } yield apply(session)

  private[mongo] def apply(session: ClientSession): Session =
    Impl(session)

  private case class Impl(session: ClientSession) extends Session {
    override def transactionScoped: ZIO[Scope, Throwable, Unit] = {
      def transacting(t: Boolean) = MongoClient.stateRef.update(_.copy(transacting = t))

      ZIO.acquireReleaseExit(
        ZIO.attempt(session.startTransaction()) *> transacting(true)
      ) {
        case (_, Exit.Success(_)) => transacting(false) *> session.commitTransaction().empty.orDie
        case (_, Exit.Failure(_)) => transacting(false) *> session.abortTransaction().empty.orDie
      }
    }

    override def transactionalMask[R, E, A](k: TransactionRestorer => ZIO[R, E, A]): ZIO[R, E, A] =
      ZIO.scoped[R](for {
        current <- MongoClient.currentSession.debug("current session")
        restorer = TransactionRestorer(current)
        result  <- transactionScoped.orDie *> k(restorer)
      } yield result)
  }
}
