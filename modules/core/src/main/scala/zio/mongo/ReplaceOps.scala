package zio.mongo

import zio.bson.BsonEncoder

trait ReplaceOps {

  def replace: ReplaceBuilder[Collection]

}

trait ReplaceBuilder[-R] {
  def one[Q: BsonEncoder, U: BsonEncoder](q: Q, u: U): ReplaceBuilder[R]
}
