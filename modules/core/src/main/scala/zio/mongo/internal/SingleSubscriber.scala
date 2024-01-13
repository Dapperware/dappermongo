package zio.mongo.internal

import dappermongo.internal.InterruptibleSubscriber
import org.reactivestreams.Subscription
import zio.{Promise, Scope, Task, UIO, URIO, Unsafe, ZIO}

import java.util.concurrent.atomic.AtomicBoolean

object SingleSubscriber {

  def make[A]: URIO[Scope, InterruptibleSubscriber[A]] = for {
    subscription <- ZIO.acquireRelease(
                      Promise.make[Throwable, Subscription]
                    )(_.poll.some.flatMap(_.map(_.cancel())).orElse(ZIO.unit))
    promise <- Promise.make[Throwable, Option[A]]
  } yield new InterruptibleSubscriber[A] {
    private val shouldCancel = new AtomicBoolean(false)

    override def interrupt: UIO[Unit] = {
      shouldCancel.set(true)
      subscription.interrupt.unit
    }

    override def await: Task[Option[A]] =
      promise.await

    override def onSubscribe(s: Subscription): Unit =
      if (shouldCancel.getAndSet(true)) s.cancel()
      else {
        Unsafe.unsafe(implicit u => subscription.unsafe.done(ZIO.succeed(s)))
        s.request(Int.MaxValue)
      }

    override def onNext(t: A): Unit =
      Unsafe.unsafe(implicit u => promise.unsafe.done(ZIO.some(t)))

    override def onError(t: Throwable): Unit =
      Unsafe.unsafe(implicit u => promise.unsafe.done(ZIO.fail(t)))

    override def onComplete(): Unit =
      Unsafe.unsafe(implicit u => promise.unsafe.done(ZIO.none))
  }

}
