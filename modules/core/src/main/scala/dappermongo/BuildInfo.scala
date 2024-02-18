package dappermongo

import reactivemongo.api.bson.{BSONDocument, BSONDocumentHandler, Macros}

case class BuildInfo(
  version: String,
  gitVersion: String,
  sysInfo: String,
  loaderFlags: String,
  compilerFlags: String,
  allocator: String,
  versionArray: List[Int],
  openssl: BSONDocument,
  javascriptEngine: String,
  bits: Int,
  debug: Boolean,
  maxBsonObjectSize: Int,
  storageEngines: List[String],
  ok: Int
)

object BuildInfo {
  implicit val bsonHandler: BSONDocumentHandler[BuildInfo] = Macros.handler[BuildInfo]
}
