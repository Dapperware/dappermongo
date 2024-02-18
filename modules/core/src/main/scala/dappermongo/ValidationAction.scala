package dappermongo

sealed abstract class ValidationAction(private val value: com.mongodb.client.model.ValidationAction) {
  def toJava: com.mongodb.client.model.ValidationAction = value

}

object ValidationAction {
  case object Error extends ValidationAction(com.mongodb.client.model.ValidationAction.ERROR)
  case object Warn  extends ValidationAction(com.mongodb.client.model.ValidationAction.WARN)
}
