package zio.mongo.results

trait Result {

  def wasAcknowledged: Boolean

}
