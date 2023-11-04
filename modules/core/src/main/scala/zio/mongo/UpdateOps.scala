package zio.mongo

import zio.ZIO
import zio.bson.BsonEncoder

trait UpdateOps {}

trait UpdateBuilder[-R] {
  def one[Q: BsonEncoder, U: BsonEncoder](q: Q, u: U): ZIO[R, Throwable, Unit]
}
