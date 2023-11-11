package zio.mongo

import com.mongodb.reactivestreams.client.ClientSession
import zio.interop.reactivestreams.publisherToStream
import zio.mongo.Session.TransactionRestorer
import zio.{Exit, Scope, ZIO}

trait Session {

  def transactionScoped: ZIO[Scope, Throwable, Unit]

  def transactional[R, E, A](zio: => ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.scoped[R](transactionScoped.orDie *> zio)

  def transactionalMask[R, E, A](k: TransactionRestorer => ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.scoped[R](for {
      current <- MongoClient.sessionRef.get
      restorer = TransactionRestorer(current)
      result  <- transactionScoped.orDie *> k(restorer)
    } yield result)

}

object Session {
  sealed trait TransactionRestorer {
    def apply[R, E, A](effect: => ZIO[R, E, A]): ZIO[R, E, A]
  }

  object TransactionRestorer {
    def apply(current: Option[ClientSession]): TransactionRestorer =
      new TransactionRestorer {
        override def apply[R, E, A](effect: => ZIO[R, E, A]): ZIO[R, E, A] =
          MongoClient.sessionRef.locally(current)(effect)
      }
  }

  def make(session: ClientSession): ZIO[Scope, Nothing, Session] =
    for {
      _ <- MongoClient.sessionRef.locallyScoped(Some(session))
    } yield apply(session)

  private[mongo] def apply(session: ClientSession): Session =
    Impl(session)

  private case class Impl(session: ClientSession) extends Session {
    override def transactionScoped: ZIO[Scope, Throwable, Unit] =
      ZIO.acquireReleaseExit(
        ZIO.attempt(session.startTransaction())
      ) {
        case (_, Exit.Success(_)) => session.commitTransaction().toZIOStream(2).runDrain.orDie
        case (_, Exit.Failure(_)) => session.abortTransaction().toZIOStream(2).runDrain.orDie
      }
  }
}
