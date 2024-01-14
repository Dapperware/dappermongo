package dappermongo

import com.mongodb.reactivestreams.client.MongoDatabase
import org.bson.BsonDocument
import zio.bson.BsonEncoder
import zio.{ZIO, ZLayer}

trait Database
    extends FindOps
    with UpdateOps
    with InsertOps
    with DeleteOps
    with IndexOps
    with DistinctOps
    with ReplaceOps
    with CountOps {
  def collection(name: String): Collection
}

object Database {

  private[dappermongo] def apply(database: MongoDatabase): Database = Impl(database)

  def make(dbName: String): ZIO[MongoClient, Throwable, Database] =
    ZIO.serviceWithZIO[MongoClient](_.database(dbName))

  def named(dbName: String): ZLayer[MongoClient, Throwable, Database] =
    ZLayer(make(dbName))

  private case class Impl(database: MongoDatabase) extends Database {
    self =>
    override def collection(name: String): Collection = Collection(name)

    override def delete: DeleteBuilder[Collection] = DeleteBuilder(database)
    override def update: UpdateBuilder[Collection] = UpdateBuilder(database)
    override def insert: InsertBuilder[Collection] = InsertBuilder(database)
    override def findAll: FindBuilder[Collection]  = FindBuilder(database)
    override def find[Q: BsonEncoder](q: Q): FindBuilder[Collection] =
      FindBuilder(database, QueryBuilderOptions(filter = Some(() => BsonEncoder[Q].toBsonValue(q).asDocument())))

    override def find[Q: BsonEncoder, P: BsonEncoder](q: Q, p: P): FindBuilder[Collection] =
      FindBuilder(
        database,
        QueryBuilderOptions(
          filter = Some(() => BsonEncoder[Q].toBsonValue(q).asDocument()),
          projection = Some(() => BsonEncoder[P].toBsonValue(p).asDocument())
        )
      )

    override def index: IndexBuilder[Collection] = IndexBuilder.Impl(database)

    override def distinct(field: String): DistinctBuilder[Collection] = DistinctBuilder(database, field)

    override def replace: ReplaceBuilder[Collection] = ReplaceBuilder(database)

    override def count: CountBuilder[Collection] = CountBuilder(database)
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
