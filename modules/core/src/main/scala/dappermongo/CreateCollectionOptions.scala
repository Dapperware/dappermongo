package dappermongo

import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.msb._
import zio.Duration

object CreateCollectionOptions {
  lazy val default = CreateCollectionOptions()
}

case class CreateCollectionOptions(
  maxDocuments: Option[Long] = None,
  capped: Option[Boolean] = None,
  sizeInBytes: Option[Long] = None,
  storageEngineOptions: Option[BSONDocument] = None,
  indexOptionDefaults: Option[IndexOptionDefaults] = None,
  validationOptions: Option[ValidationOptions] = None,
  collation: Option[Collation] = None,
  expireAfter: Option[Duration] = None,
  timeSeriesOptions: Option[TimeSeriesOptions] = None,
  changeStreamPreAndPostImagesOptions: Option[ChangeStreamPreAndPostImagesOptions] = None,
  clusteredIndexOptions: Option[ClusteredIndexOptions] = None,
  encryptedFields: Option[BSONDocument] = None
) {
  def toJava: com.mongodb.client.model.CreateCollectionOptions = {
    val builder = new com.mongodb.client.model.CreateCollectionOptions()
    maxDocuments.foreach(builder.maxDocuments)
    capped.foreach(builder.capped)
    sizeInBytes.foreach(builder.sizeInBytes)
    storageEngineOptions.foreach(o => builder.storageEngineOptions(o))
    indexOptionDefaults.foreach(o => builder.indexOptionDefaults(o.toJava))
    validationOptions.foreach(o => builder.validationOptions(o.toJava))
    collation.foreach(o => builder.collation(o.asJava))
    expireAfter.foreach(o => builder.expireAfter(o.toMillis, java.util.concurrent.TimeUnit.MILLISECONDS))
    timeSeriesOptions.foreach(o => builder.timeSeriesOptions(o.toJava))
    changeStreamPreAndPostImagesOptions.foreach(o => builder.changeStreamPreAndPostImagesOptions(o.toJava))
    clusteredIndexOptions.foreach(o => builder.clusteredIndexOptions(o.toJava))
    encryptedFields.foreach(o => builder.encryptedFields(o))
    builder
  }
}
