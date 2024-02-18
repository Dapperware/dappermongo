package dappermongo

case class ChangeStreamPreAndPostImagesOptions(
  enabled: Boolean = false
) {
  def toJava: com.mongodb.client.model.ChangeStreamPreAndPostImagesOptions =
    new com.mongodb.client.model.ChangeStreamPreAndPostImagesOptions(enabled)
}
