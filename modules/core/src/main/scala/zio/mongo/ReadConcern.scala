package zio.mongo

import com.mongodb.{ReadConcernLevel, ReadConcern => JReadConcern}
import zio.Config

class ReadConcern private (private[mongo] val wrapped: JReadConcern)

object ReadConcern {
  def apply(level: Option[Level] = None): ReadConcern =
    level.fold(new ReadConcern(JReadConcern.DEFAULT))(l => new ReadConcern(new JReadConcern(l.wrapped)))

  sealed abstract class Level private (private[mongo] val wrapped: ReadConcernLevel)

  object Level {
    case object Local        extends Level(ReadConcernLevel.LOCAL)
    case object Majority     extends Level(ReadConcernLevel.MAJORITY)
    case object Linearizable extends Level(ReadConcernLevel.LINEARIZABLE)
    case object Available    extends Level(ReadConcernLevel.AVAILABLE)
  }

  val Local: ReadConcern        = new ReadConcern(JReadConcern.LOCAL)
  val Majority: ReadConcern     = new ReadConcern(JReadConcern.MAJORITY)
  val Linearizable: ReadConcern = new ReadConcern(JReadConcern.LINEARIZABLE)
  val Available: ReadConcern    = new ReadConcern(JReadConcern.AVAILABLE)

  val config: Config[ReadConcern] = ???

}
