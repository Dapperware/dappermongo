package dappermongo

import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.msb._

case class ValidationOptions(
  validator: Option[BSONDocument] = None,
  validationLevel: Option[ValidationLevel] = None,
  validationAction: Option[ValidationAction] = None
) {
  def toJava: com.mongodb.client.model.ValidationOptions = {
    val builder = new com.mongodb.client.model.ValidationOptions()
    validator.foreach(o => builder.validator(o))
    validationLevel.foreach(o => builder.validationLevel(o.toJava))
    validationAction.foreach(o => builder.validationAction(o.toJava))
    builder
  }
}
