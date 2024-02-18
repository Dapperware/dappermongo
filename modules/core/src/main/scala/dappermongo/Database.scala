package dappermongo

import com.mongodb.reactivestreams.client.{ClientSession, MongoDatabase}
import dappermongo.aggregate.Pipeline
import dappermongo.internal.DocumentEncodedFn
import reactivemongo.api.bson.BSONDocumentWriter
import zio.{Trace, ZIO, ZLayer}

trait Database
    extends AggregateOps
    with CollectionOps
    with CountOps
    with DeleteOps
    with DiagnosticOps
    with DistinctOps
    with FindOps
    with IndexOps
    with InsertOps
    with ReplaceOps
    with UpdateOps {

  def name: String
}

object Database {

  private[dappermongo] def apply(database: MongoDatabase, sessionStorage: SessionStorage[ClientSession]): Database =
    Impl(database, sessionStorage)

  def make(dbName: String): ZIO[MongoClient, Throwable, Database] =
    ZIO.serviceWithZIO[MongoClient](_.database(dbName))

  def named(dbName: String)(implicit trace: Trace): ZLayer[MongoClient, Throwable, Database] =
    ZLayer(make(dbName))

  private case class Impl(database: MongoDatabase, sessionStorage: SessionStorage[ClientSession]) extends Database {
    self =>

    override lazy val collection                     = CollectionOps.Builder(database, sessionStorage)
    override lazy val diagnostics: DiagnosticBuilder = DiagnosticBuilder(database)

    def name: String = database.getName

    override def aggregate(pipeline: Pipeline): AggregateBuilder[Collection] =
      AggregateBuilder(database, pipeline, sessionStorage)

    override def count: CountBuilder[Collection] = CountBuilder(database, sessionStorage)

    override def delete: DeleteBuilder[Collection]                    = DeleteBuilder(database, sessionStorage)
    override def distinct(field: String): DistinctBuilder[Collection] = DistinctBuilder(database, field, sessionStorage)
    override def findAll: FindBuilder[Collection]                     = FindBuilder(database, QueryBuilderOptions(), sessionStorage)
    override def find[Q](q: Q)(implicit ev: BSONDocumentWriter[Q]): FindBuilder[Collection] =
      FindBuilder(database, QueryBuilderOptions(filter = Some(DocumentEncodedFn(ev.writeTry(q)))), sessionStorage)
    override def find[Q, P](query: Q, projection: P)(implicit
      evQ: BSONDocumentWriter[Q],
      evP: BSONDocumentWriter[P]
    ): FindBuilder[Collection] =
      FindBuilder(
        database,
        QueryBuilderOptions(
          filter = Some(DocumentEncodedFn(evQ.writeTry(query))),
          projection = Some(DocumentEncodedFn(evP.writeTry(projection)))
        ),
        sessionStorage
      )

    override def index: IndexBuilder[Collection] = IndexBuilder.Impl(database, sessionStorage)

    override def insert: InsertBuilder[Collection] = InsertBuilder(database, sessionStorage)

    override def replace: ReplaceBuilder[Collection] = ReplaceBuilder(database, sessionStorage)

    override def update: UpdateBuilder[Collection] = UpdateBuilder(database, sessionStorage)
  }
}

private case class QueryBuilderOptions(
  filter: Option[DocumentEncodedFn] = None,
  projection: Option[DocumentEncodedFn] = None,
  sort: Option[DocumentEncodedFn] = None,
  skip: Option[Int] = None,
  allowDiskUse: Option[Boolean] = None,
  collation: Option[Collation] = None,
  comment: Option[String] = None,
  hint: Option[String] = None,
  noCursorTimeout: Option[Boolean] = None,
  explain: Option[Boolean] = None
)
