package dappermongo.results

import reactivemongo.api.bson.BSONValue

case class InsertedOne(
  insertedId: Option[BSONValue]
)

case class InsertedMany(
  insertedIds: Map[Int, BSONValue]
)
