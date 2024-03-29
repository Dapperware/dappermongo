package dappermongo

import java.time.Instant
import zio.test.{TestClock, ZIOSpecDefault, assertTrue}
import zio.{Clock, URIO, ZIO}

import zio.durationInt

object ObjectIdSpec extends ZIOSpecDefault {

  val clockBasedFactory: URIO[Any, ObjectId.Factory] =
    ObjectId.Factory.make(Clock.instant.map(ObjectId.fromInstant(_, timeOnly = true)))

  val constantFactory: URIO[Any, ObjectId.Factory] = {
    val id = ObjectId.fromInstant(Instant.EPOCH, timeOnly = true)
    ObjectId.Factory.make(ZIO.succeed(id))
  }
  val spec = suite("ObjectId")(
    test("make") {
      for {
        id <- ObjectId.make
      } yield assertTrue(id.timestamp > 0)
    },
    test("override factory - test clock") {
      for {
        factory <- clockBasedFactory
        ids <- ObjectId.withFactory(factory)(
                 ZIO.collectAll(
                   List(
                     ObjectId.make,
                     TestClock.adjust(20.seconds) *> ObjectId.make,
                     TestClock.adjust(60.seconds) *> ObjectId.make,
                     TestClock.adjust(24.hours) *> ObjectId.make
                   )
                 )
               )
      } yield assertTrue(
        ids.map(_.toHexString) == List(
          "000000000000000000000000",
          "000000140000000000000000",
          "000000500000000000000000",
          "000151d00000000000000000"
        )
      )
    },
    test("override factory - constant") {
      for {
        factory <- constantFactory
        ids <- ObjectId.withFactory(factory)(
                 ZIO.collectAll(
                   List(
                     ObjectId.make,
                     ObjectId.make,
                     ObjectId.make,
                     ObjectId.make
                   )
                 )
               )
      } yield assertTrue(ids.map(_.toHexString) == List.fill(4)("000000000000000000000000"))
    }
  )

}
