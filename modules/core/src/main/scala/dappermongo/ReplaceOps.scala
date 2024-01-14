package dappermongo

import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.{MongoCollection, MongoDatabase}
import dappermongo.results.{Result, Updated}
import org.bson.BsonValue
import org.bson.conversions.Bson
import zio.ZIO
import zio.bson.BsonEncoder

import dappermongo.internal.PublisherOps

trait ReplaceOps {

  def replace: ReplaceBuilder[Collection]

}

trait ReplaceBuilder[-R] {
  def one[Q: BsonEncoder, U: BsonEncoder](q: Q, u: U): ZIO[R, Throwable, Result[Updated]]

  def upsert(upsert: Boolean): ReplaceBuilder[R]

  def bypassDocumentValidation(bypassDocumentValidation: Boolean): ReplaceBuilder[R]

  def collation(collation: Collation): ReplaceBuilder[R]

  def hint(hint: String): ReplaceBuilder[R]

  def variables(variables: Bson): ReplaceBuilder[R]

}

object ReplaceBuilder {

  def apply(database: MongoDatabase): ReplaceBuilder[Collection] =
    Impl(database, ReplaceBuilderOptions())

  private case class ReplaceBuilderOptions(
    upsert: Option[Boolean] = None,
    bypassDocumentValidation: Option[Boolean] = None,
    collation: Option[Collation] = None,
    hint: Option[String] = None,
    variables: Option[Bson] = None
  )

  private case class Impl(database: MongoDatabase, options: ReplaceBuilderOptions) extends ReplaceBuilder[Collection] {
    override def one[Q: BsonEncoder, U: BsonEncoder](q: Q, u: U): ZIO[Collection, Throwable, Result[Updated]] =
      ZIO.serviceWithZIO { collection =>
        MongoClient.currentSession.flatMap { session =>
          val coll = withLocalSettings(database.getCollection(collection.name, classOf[BsonValue]), collection)

          val query = BsonEncoder[Q].toBsonValue(q).asDocument()
          val value = BsonEncoder[U].toBsonValue(u)

          session
            .fold(coll.replaceOne(query, value, toReplaceOptions(options)))(
              coll.replaceOne(_, query, value, toReplaceOptions(options))
            )
            .single
            .map(
              _.fold[Result[Updated]](Result.Unacknowledged)(result =>
                Result
                  .Acknowledged(Updated(result.getMatchedCount, result.getModifiedCount, Option(result.getUpsertedId)))
              )
            )
        }
      }

    override def upsert(upsert: Boolean): ReplaceBuilder[Collection] =
      copy(options = options.copy(upsert = Some(upsert)))

    override def bypassDocumentValidation(bypassDocumentValidation: Boolean): ReplaceBuilder[Collection] =
      copy(options = options.copy(bypassDocumentValidation = Some(bypassDocumentValidation)))

    override def collation(collation: Collation): ReplaceBuilder[Collection] =
      copy(options = options.copy(collation = Some(collation)))

    override def hint(hint: String): ReplaceBuilder[Collection] =
      copy(options = options.copy(hint = Some(hint)))

    override def variables(variables: Bson): ReplaceBuilder[Collection] =
      copy(options = options.copy(variables = Some(variables)))

    private def withLocalSettings[T](collection: MongoCollection[T], options: Collection): MongoCollection[T] = {
      var coll = collection
      options.readConcern.foreach(rc => coll = coll.withReadConcern(rc.wrapped))
      options.readPreference.foreach(rp => coll = coll.withReadPreference(rp.wrapped))
      options.writeConcern.foreach(wc => coll = coll.withWriteConcern(wc.wrapped))
      coll
    }

    private def toReplaceOptions(options: ReplaceBuilderOptions): ReplaceOptions =
      new ReplaceOptions()
        .upsert(java.lang.Boolean.valueOf(options.upsert.getOrElse(false)))
        .bypassDocumentValidation(java.lang.Boolean.valueOf(options.bypassDocumentValidation.getOrElse(false)))
        .collation(options.collation.map(_.asJava).orNull)
        .hintString(options.hint.orNull)
        .let(options.variables.orNull)

  }
}
