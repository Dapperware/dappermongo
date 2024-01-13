package dappermongo.results

import org.bson.BsonValue

case class Updated(
  matched: Long,
  modified: Long,
  upsertedId: Option[BsonValue]
)
