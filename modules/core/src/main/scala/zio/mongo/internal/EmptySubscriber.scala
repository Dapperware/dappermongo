package zio.mongo.internal

import dappermongo.internal.InterruptibleSubscriber
import org.reactivestreams.Subscription
import zio.{Promise, Scope, Task, UIO, URIO, Unsafe, ZIO}

import java.util.concurrent.atomic.AtomicBoolean

object EmptySubscriber {

  def make[A]: URIO[Scope, InterruptibleSubscriber[A]] = for {
    subscription <- ZIO.acquireRelease(
                      Promise.make[Throwable, Subscription]
                    )(_.poll.flatMap(_.fold(ZIO.unit)(_.foldZIO(_ => ZIO.unit, sub => ZIO.succeed(sub.cancel())))))
    promise <- Promise.make[Throwable, Unit]
  } yield new InterruptibleSubscriber[A] {
    private val shouldCancel = new AtomicBoolean

    override def interrupt: UIO[Unit] = {
      shouldCancel.set(true)
      subscription.interrupt.unit
    }

    override def await: Task[Option[A]] = promise.await.as(None)

    override def onSubscribe(s: Subscription): Unit =
      if (shouldCancel.getAndSet(true)) s.cancel()
      else {
        Unsafe.unsafe(implicit u => subscription.unsafe.done(ZIO.succeed(s)))
        s.request(Int.MaxValue)
      }

    override def onNext(t: A): Unit = ()

    override def onError(t: Throwable): Unit =
      Unsafe.unsafe(implicit u => promise.unsafe.done(ZIO.fail(t)))

    override def onComplete(): Unit =
      Unsafe.unsafe(implicit u => promise.unsafe.done(ZIO.unit))
  }

}
