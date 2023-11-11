package zio.mongo.results

class DeleteResult(private[mongo] val wrapped: com.mongodb.client.result.DeleteResult) extends Result {
  def wasAcknowledged: Boolean = wrapped.wasAcknowledged()

  def deletedCount: Long = wrapped.getDeletedCount
}

object DeleteResult {
  lazy val Unacknowledged: DeleteResult = new DeleteResult(com.mongodb.client.result.DeleteResult.unacknowledged())
}
