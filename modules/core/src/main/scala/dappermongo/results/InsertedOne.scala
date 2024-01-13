package dappermongo.results

import org.bson.BsonValue

case class InsertedOne(
  insertedId: Option[BsonValue]
)

case class InsertedMany(
  insertedIds: Map[Int, BsonValue]
)
