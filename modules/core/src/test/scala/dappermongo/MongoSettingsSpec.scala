package dappermongo

import zio.test._
import zio.{ConfigProvider, ZIO}

object MongoSettingsSpec extends ZIOSpecDefault {

  val provider = ConfigProvider.fromMap(
    Map(
      "connectionString"        -> "mongodb+srv://localhost/?appName=test",
      "mongodb.applicationName" -> "test",
      "mongodb.readConcern"     -> "local",
      "mongodb.writeConcern"    -> "local",
      "mongodb.readPreference"  -> "local",
      "mongodb.retryWrites"     -> "true",
      "mongodb.retryReads"      -> "true"
    )
  )

  val spec = suite("MongoSettings")(
    test("can load connectionString") {
      for {
        _  <- ZIO.withConfigProviderScoped(provider)
        cs <- ZIO.config(ConnectionString.config.nested("connectionString"))
      } yield assertTrue(
        cs.isSrv,
        cs.applicationName.contains("test"),
        cs.readConcern.isEmpty
      )
    }
  )

}
