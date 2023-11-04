package zio.mongo

import zio.Config

import com.mongodb.MongoCredential

class Credential private (wrapped: MongoCredential) {}

object Credential {
  val config: Config[Credential] = ???
}
