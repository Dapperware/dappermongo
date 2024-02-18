package dappermongo

import zio.Duration

case class TimeSeriesOptions(
  timeField: String,
  metaField: Option[String] = None,
  granularity: Option[TimeSeriesGranularity] = None,
  bucketMaxSpan: Option[Duration] = None,
  bucketRounding: Option[Duration] = None
) {
  def toJava: com.mongodb.client.model.TimeSeriesOptions = {
    val builder = new com.mongodb.client.model.TimeSeriesOptions(timeField)
    metaField.foreach(builder.metaField)
    builder.bucketMaxSpan(
      bucketMaxSpan.fold[java.lang.Long](null)(_.toMillis),
      java.util.concurrent.TimeUnit.MILLISECONDS
    )
    builder.bucketRounding(
      bucketRounding.fold[java.lang.Long](null)(_.toMillis),
      java.util.concurrent.TimeUnit.MILLISECONDS
    )
    builder.granularity(granularity.fold[com.mongodb.client.model.TimeSeriesGranularity](null)(_.toJava))
    builder
  }
}
