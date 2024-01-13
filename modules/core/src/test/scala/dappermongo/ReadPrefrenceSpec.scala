package dappermongo

import zio.test.{ZIOSpecDefault, assertTrue}
import zio.{ConfigProvider, ZIO}

object ReadPrefrenceSpec extends ZIOSpecDefault {

  val spec = suite("ReadPreference")(
    suite("config")(
      test("can load from a string") {
        val provider = ConfigProvider.fromMap(
          Map(
            "pr"  -> "primary",
            "sr"  -> "secondary",
            "spr" -> "secondaryPreferred",
            "ppr" -> "primaryPreferred",
            "n"   -> "nearest"
          )
        )

        ZIO.withConfigProvider(provider)(
          for {
            pr  <- ZIO.config(ReadPreference.config.nested("pr"))
            sr  <- ZIO.config(ReadPreference.config.nested("sr"))
            spr <- ZIO.config(ReadPreference.config.nested("spr"))
            ppr <- ZIO.config(ReadPreference.config.nested("ppr"))
            n   <- ZIO.config(ReadPreference.config.nested("n"))
          } yield assertTrue(
            pr == ReadPreference.Primary,
            sr == ReadPreference.Secondary(None),
            spr == ReadPreference.SecondaryPreferred(None),
            ppr == ReadPreference.PrimaryPreferred(None),
            n == ReadPreference.Nearest(None)
          )
        )
      },
      test("can load from object") {
        val provider = ConfigProvider.fromMap(
          Map(
            "pr.name"  -> "primary",
            "sr.name"  -> "secondary",
            "spr.name" -> "secondaryPreferred",
            "ppr.name" -> "primaryPreferred",
            "n.name"   -> "nearest"
          )
        )

        ZIO.withConfigProvider(provider)(
          for {
            pr  <- ZIO.config(ReadPreference.config.nested("pr"))
            sr  <- ZIO.config(ReadPreference.config.nested("sr"))
            spr <- ZIO.config(ReadPreference.config.nested("spr"))
            ppr <- ZIO.config(ReadPreference.config.nested("ppr"))
            n   <- ZIO.config(ReadPreference.config.nested("n"))
          } yield assertTrue(
            pr == ReadPreference.Primary,
            sr == ReadPreference.Secondary(None),
            spr == ReadPreference.SecondaryPreferred(None),
            ppr == ReadPreference.PrimaryPreferred(None),
            n == ReadPreference.Nearest(None)
          )
        )
      }
    )
  )

}
