package zio.mongo

import zio.ZIO
import zio.bson.BsonEncoder

trait InsertOps {}

trait InsertBuilder[-R] {
  def one[U: BsonEncoder](u: U): ZIO[R, Throwable, Unit]
}
