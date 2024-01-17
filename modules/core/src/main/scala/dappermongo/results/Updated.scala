package dappermongo.results

import reactivemongo.api.bson.BSONValue

case class Updated(
  matched: Long,
  modified: Long,
  upsertedId: Option[BSONValue]
)
