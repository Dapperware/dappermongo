package zio.mongo

import com.mongodb.reactivestreams.client.MongoDatabase
import org.bson.BsonDocument
import zio.bson.BsonEncoder
import zio.{ZIO, ZLayer}

trait Database extends FindOps with UpdateOps with InsertOps with DeleteOps with IndexOps with DistinctOps {
  def collection(name: String): Collection
}

object Database {

  def make(dbName: String): ZIO[MongoClient, Throwable, Database] =
    ZIO.serviceWithZIO[MongoClient](_.database(dbName))

  def named(dbName: String): ZLayer[MongoClient, Throwable, Database] =
    ZLayer(make(dbName))

  private[mongo] case class Impl(database: MongoDatabase) extends Database {
    self =>
    override def collection(name: String): Collection = Collection(name)

    override def delete: DeleteBuilder[Collection] = new DeleteBuilder.Impl(database)
    override def update: UpdateBuilder[Collection] = new UpdateBuilder.Impl(database)
    override def insert: InsertBuilder[Collection] = new InsertBuilder.Impl(database)
    override def findAll: FindBuilder[Collection]  = FindBuilder.Impl(database, QueryBuilderOptions())
    override def find[Q: BsonEncoder](q: Q): FindBuilder[Collection] =
      FindBuilder.Impl(database, QueryBuilderOptions(filter = Some(() => BsonEncoder[Q].toBsonValue(q).asDocument())))

    override def find[Q: BsonEncoder, P: BsonEncoder](q: Q, p: P): FindBuilder[Collection] =
      FindBuilder.Impl(
        database,
        QueryBuilderOptions(
          filter = Some(() => BsonEncoder[Q].toBsonValue(q).asDocument()),
          projection = Some(() => BsonEncoder[P].toBsonValue(p).asDocument())
        )
      )

    override def index: IndexBuilder[Collection] = IndexBuilder.Impl(database)

    override def distinct(field: String): DistinctBuilder[Collection] = DistinctBuilder(database, field)

  }

}

private case class QueryBuilderOptions(
  filter: Option[() => BsonDocument] = None,
  projection: Option[() => BsonDocument] = None,
  sort: Option[() => BsonDocument] = None,
  skip: Option[Int] = None,
  allowDiskUse: Option[Boolean] = None,
  collation: Option[Collation] = None,
  comment: Option[String] = None,
  hint: Option[String] = None,
  noCursorTimeout: Option[Boolean] = None,
  explain: Option[Boolean] = None
)
