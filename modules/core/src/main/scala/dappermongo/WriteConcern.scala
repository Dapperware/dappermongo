package dappermongo

import com.mongodb.{WriteConcern => JWriteConcern}
import zio.{Config, Duration}

class WriteConcern private (private[dappermongo] val wrapped: JWriteConcern) {}

object WriteConcern {
  private[dappermongo] def apply(inner: JWriteConcern): WriteConcern = new WriteConcern(inner)

  sealed trait W extends Product with Serializable

  object W {
    case class Count(count: Int) extends W
    case class Tag(tag: String)  extends W
    case object Majority         extends W
  }

  def apply(
    w: Option[W] = None,
    journal: Option[Boolean] = None,
    writeTimeout: Option[Duration] = None
  ): WriteConcern = {
    val write = w match {
      case Some(W.Count(count)) => new JWriteConcern(count)
      case Some(W.Tag(tag))     => new JWriteConcern(tag)
      case Some(W.Majority)     => JWriteConcern.MAJORITY
      case None                 => JWriteConcern.ACKNOWLEDGED
    }

    new WriteConcern(
      write
        .withJournal(journal.map(java.lang.Boolean.valueOf).orNull)
        .withWTimeout(
          writeTimeout.map(_.toMillis).map(java.lang.Long.valueOf).orNull,
          java.util.concurrent.TimeUnit.MILLISECONDS
        )
    )
  }

  lazy val Acknowledged: WriteConcern   = new WriteConcern(JWriteConcern.ACKNOWLEDGED)
  lazy val W1: WriteConcern             = new WriteConcern(JWriteConcern.W1)
  lazy val W2: WriteConcern             = new WriteConcern(JWriteConcern.W2)
  lazy val W3: WriteConcern             = new WriteConcern(JWriteConcern.W3)
  lazy val Unacknowledged: WriteConcern = new WriteConcern(JWriteConcern.UNACKNOWLEDGED)
  lazy val Journaled: WriteConcern      = new WriteConcern(JWriteConcern.JOURNALED)
  lazy val Majority: WriteConcern       = new WriteConcern(JWriteConcern.MAJORITY)

  val config: Config[WriteConcern] =
    Config.string.map(_ => WriteConcern.Majority)
}
