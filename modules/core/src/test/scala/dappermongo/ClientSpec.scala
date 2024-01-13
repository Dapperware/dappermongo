package dappermongo

import zio.{ZIO, durationInt}
import zio.test.Assertion.isRight
import zio.test._

object ClientSpec extends ZIOSpecDefault {

  val spec = suite("Client")(
    test("connect to a client") {
      ZIO.scoped(MongoClient.scoped.either.map(assert(_)(isRight)))
    }
  ).provide(
    Container.testContainer,
    Container.settings
  ) @@ TestAspect.silentLogging @@ TestAspect.timeout(60.seconds)

}
