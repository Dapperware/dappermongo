package dappermongo

import com.mongodb.reactivestreams.client.MongoDatabase
import dappermongo.aggregate.Pipeline
import reactivemongo.api.bson.{BSONDocument, BSONDocumentWriter}
import zio.{ZIO, ZLayer}

trait Database
    extends AggregateOps
    with FindOps
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
    override def find[Q](q: Q)(implicit ev: BSONDocumentWriter[Q]): FindBuilder[Collection] =
      FindBuilder(database, QueryBuilderOptions(filter = Some(() => ev.writeTry(q).get)))

    override def find[Q, P](query: Q, projection: P)(implicit
      evQ: BSONDocumentWriter[Q],
      evP: BSONDocumentWriter[P]
    ): FindBuilder[Collection] =
      FindBuilder(
        database,
        QueryBuilderOptions(
          filter = Some(() => evQ.writeTry(query).get),
          projection = Some(() => evP.writeTry(projection).get)
        )
      )

    override def index: IndexBuilder[Collection] = IndexBuilder.Impl(database)

    override def distinct(field: String): DistinctBuilder[Collection] = DistinctBuilder(database, field)

    override def replace: ReplaceBuilder[Collection] = ReplaceBuilder(database)

    override def count: CountBuilder[Collection] = CountBuilder(database)

    override def aggregate(pipeline: Pipeline): AggregateBuilder[Collection] = AggregateBuilder(database, pipeline)
  }

}

private case class QueryBuilderOptions(
  filter: Option[() => BSONDocument] = None,
  projection: Option[() => BSONDocument] = None,
  sort: Option[() => BSONDocument] = None,
  skip: Option[Int] = None,
  allowDiskUse: Option[Boolean] = None,
  collation: Option[Collation] = None,
  comment: Option[String] = None,
  hint: Option[String] = None,
  noCursorTimeout: Option[Boolean] = None,
  explain: Option[Boolean] = None
)
