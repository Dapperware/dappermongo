package dappermongo.results

sealed trait Result[+Op] {

  def wasAcknowledged: Boolean

}

object Result {

  case class Acknowledged[+Op](value: Op) extends Result[Op] {
    def wasAcknowledged: Boolean = true
  }

  case object Unacknowledged extends Result[Nothing] {
    def wasAcknowledged: Boolean = false
  }

}
