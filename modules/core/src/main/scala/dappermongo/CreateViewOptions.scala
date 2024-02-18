package dappermongo

case class CreateViewOptions(
  collation: Option[Collation] = None
) {
  def toJava: com.mongodb.client.model.CreateViewOptions = {
    val builder = new com.mongodb.client.model.CreateViewOptions()
    builder.collation(collation.map(_.asJava).orNull)
    builder
  }

}

object CreateViewOptions {
  lazy val default = CreateViewOptions()
}
