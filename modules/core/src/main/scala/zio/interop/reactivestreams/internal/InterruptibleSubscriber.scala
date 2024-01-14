package zio.interop.reactivestreams.internal

import org.reactivestreams.Subscriber
import zio.{Task, UIO}

trait InterruptibleSubscriber[A] extends Subscriber[A] {
  def interrupt: UIO[Unit]

  def await: Task[Option[A]]

}
