package zio.mongo

import scala.util.control.NonFatal

import java.nio.ByteBuffer
import java.time.Instant
import java.util.Date
import org.bson.types.{ObjectId => JObjectId}
import zio.bson.{BsonDecoder, BsonEncoder}
import zio.{Chunk, FiberRef, Scope, UIO, URIO, Unsafe, ZIO}

class ObjectId private (private val inner: JObjectId) {
  def toChunk: Chunk[Byte] = Chunk.fromArray(inner.toByteArray)

  def toHexString: String = inner.toHexString

  def timestamp: Int = inner.getTimestamp

  def time: Instant = Instant.ofEpochMilli(timestamp)
}

object ObjectId {
  implicit val encoder: BsonEncoder[ObjectId] =
    BsonEncoder[JObjectId].contramap(_.inner)

  implicit val decoder: BsonDecoder[ObjectId] =
    BsonDecoder[JObjectId].map(new ObjectId(_))

  implicit val order: Ordering[ObjectId] =
    Ordering.by(_.inner)

  def fromChunk(chunk: Chunk[Byte]): Either[String, ObjectId] =
    try Right(new ObjectId(new JObjectId(chunk.toArray)))
    catch { case NonFatal(e) => Left(e.getMessage) }

  def fromByteBuffer(buffer: ByteBuffer): Either[String, ObjectId] =
    fromJObjectId(new JObjectId(buffer))

  def parse(hexString: String): Either[String, ObjectId] =
    fromJObjectId(new JObjectId(hexString))

  def fromInstant(instant: Instant, timeOnly: Boolean = false): ObjectId =
    if (!timeOnly) new ObjectId(new JObjectId(Date.from(instant)))
    else {
      val buffer = ByteBuffer.allocate(12)
      buffer.putInt(instant.getEpochSecond.toInt)
      buffer.put(Array.fill[Byte](8)(0.toByte))
      buffer.rewind()
      new ObjectId(new JObjectId(buffer))
    }

  private def fromJObjectId(id: => JObjectId): Either[String, ObjectId] =
    try Right(new ObjectId(id))
    catch { case NonFatal(e) => Left(e.getMessage) }

  def make: UIO[ObjectId] =
    Factory.current.get.flatMap(_.newObjectId)

  def setFactoryScoped(factory: => Factory): ZIO[Scope, Nothing, Unit] =
    Factory.current.locallyScoped(factory)

  def withFactory[R, E, A](factory: => Factory)(zio: => ZIO[R, E, A]): ZIO[R, E, A] =
    Factory.current.locally(factory)(zio)

  trait Factory {
    def newObjectId: UIO[ObjectId]
  }

  object Factory {

    def make[R](factory: => URIO[R, ObjectId]): URIO[R, Factory] =
      ZIO.environmentWith[R] { r =>
        new Factory {
          override def newObjectId: UIO[ObjectId] = factory.provideEnvironment(r)
        }
      }

    private[mongo] lazy val default: Factory = new Factory {
      override def newObjectId: UIO[ObjectId] = ZIO.succeed(new ObjectId(JObjectId.get()))
    }

    private[mongo] val current: FiberRef[Factory] = FiberRef.unsafe.make(default)(Unsafe.unsafe)
  }
}
