package zio.mongo

import zio.Config

class ReadPreference {}

object ReadPreference {
  val config: Config[ReadPreference] = ???
}
