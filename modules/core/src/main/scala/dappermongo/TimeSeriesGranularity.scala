package dappermongo

sealed abstract class TimeSeriesGranularity(private val value: com.mongodb.client.model.TimeSeriesGranularity) {
  def toJava: com.mongodb.client.model.TimeSeriesGranularity = value

}

object TimeSeriesGranularity {
  case object Seconds extends TimeSeriesGranularity(com.mongodb.client.model.TimeSeriesGranularity.SECONDS)
  case object Minutes extends TimeSeriesGranularity(com.mongodb.client.model.TimeSeriesGranularity.MINUTES)
  case object Hours   extends TimeSeriesGranularity(com.mongodb.client.model.TimeSeriesGranularity.HOURS)
}
