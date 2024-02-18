package dappermongo

sealed abstract class ValidationLevel(private val value: com.mongodb.client.model.ValidationLevel) {
  def toJava: com.mongodb.client.model.ValidationLevel = value
}

object ValidationLevel {
  case object Off      extends ValidationLevel(com.mongodb.client.model.ValidationLevel.OFF)
  case object Strict   extends ValidationLevel(com.mongodb.client.model.ValidationLevel.STRICT)
  case object Moderate extends ValidationLevel(com.mongodb.client.model.ValidationLevel.MODERATE)
}
