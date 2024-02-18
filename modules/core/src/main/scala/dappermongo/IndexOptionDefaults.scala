package dappermongo

import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.msb._

case class IndexOptionDefaults(
  storageEngine: Option[BSONDocument] = None
) {
  def toJava: com.mongodb.client.model.IndexOptionDefaults = {
    val builder = new com.mongodb.client.model.IndexOptionDefaults()
    storageEngine.foreach(o => builder.storageEngine(o))
    builder
  }
}
