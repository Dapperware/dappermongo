package zio.mongo

import org.bson.BsonDocument
import zio.{ZIO, ZLayer}

trait Database extends QueryOps with UpdateOps with InsertOps {
  def collection(name: String): Collection
}

object Database {

  def make(dbName: String): ZIO[MongoClient, Throwable, Database] = ???

  def named(dbName: String): ZLayer[MongoClient, Throwable, Database] =
    ZLayer(make(dbName))

}

private[mongo] case class DatabaseImpl(name: String) extends Database { self =>
  override def collection(name: String): Collection = CollectionImpl(name)

  def update: UpdateBuilder[Collection] = ???

  def insert: InsertBuilder[Collection] = ???

  def findAll: QueryBuilder[Collection] = ???

}

private case class QueryBuilderOptions(
  filter: Option[BsonDocument] = None,
  projection: Option[BsonDocument] = None,
  sort: Option[BsonDocument] = None,
  skip: Option[Int] = None,
  allowDiskUse: Option[Boolean] = None,
  collation: Option[Collation] = None,
  comment: Option[String] = None,
  hint: Option[BsonDocument] = None,
  noCursorTimeout: Option[Boolean] = None,
  explain: Option[Boolean] = None
)
