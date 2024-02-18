package dappermongo

import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.msb._

case class ClusteredIndexOptions(
  key: BSONDocument,
  unique: Boolean,
  name: Option[String] = None
) {
  def toJava: com.mongodb.client.model.ClusteredIndexOptions = {
    val builder = new com.mongodb.client.model.ClusteredIndexOptions(key, unique)
    name.foreach(builder.name)
    builder
  }
}
