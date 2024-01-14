package dappermongo

import org.reactivestreams.Publisher
import zio.interop.reactivestreams.internal.{EmptySubscriber, SingleSubscriber}
import zio.{Task, ZIO}

package object internal {
  final implicit class PublisherOps[A](private val publisher: Publisher[A]) extends AnyVal {
    def single: Task[Option[A]] = ZIO.scoped(SingleSubscriber.make[A].flatMap { subscriber =>
      publisher.subscribe(subscriber)
      subscriber.await.onInterrupt(subscriber.interrupt)
    })

    def empty: Task[Unit] = ZIO.scoped(EmptySubscriber.make[A].flatMap { subscriber =>
      publisher.subscribe(subscriber)
      subscriber.await.onInterrupt(subscriber.interrupt).unit
    })
  }
}
