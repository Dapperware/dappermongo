package dappermongo

import zio.ZIO
import zio.test.Assertion.isRight
import zio.test._

import zio.durationInt

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
