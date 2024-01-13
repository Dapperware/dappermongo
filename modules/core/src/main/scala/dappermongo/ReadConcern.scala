package dappermongo

import com.mongodb.{ReadConcern => JReadConcern, ReadConcernLevel}
import zio.{Chunk, Config}

class ReadConcern private (private[dappermongo] val wrapped: JReadConcern)

object ReadConcern {

  private[dappermongo] def apply(inner: JReadConcern): ReadConcern = new ReadConcern(inner)

  def apply(level: Option[Level] = None): ReadConcern =
    level.fold(new ReadConcern(JReadConcern.DEFAULT))(l => new ReadConcern(new JReadConcern(l.wrapped)))

  sealed abstract class Level private (private[dappermongo] val wrapped: ReadConcernLevel)

  object Level {
    case object Local        extends Level(ReadConcernLevel.LOCAL)
    case object Majority     extends Level(ReadConcernLevel.MAJORITY)
    case object Linearizable extends Level(ReadConcernLevel.LINEARIZABLE)
    case object Available    extends Level(ReadConcernLevel.AVAILABLE)

    val config: Config[Level] = Config.string.mapOrFail {
      case "local"        => Right(Local)
      case "majority"     => Right(Majority)
      case "linearizable" => Right(Linearizable)
      case "available"    => Right(Available)
      case other          => Left(Config.Error.InvalidData(Chunk.empty, s"Invalid read concern level: $other"))
    }
  }

  val Local: ReadConcern        = new ReadConcern(JReadConcern.LOCAL)
  val Majority: ReadConcern     = new ReadConcern(JReadConcern.MAJORITY)
  val Linearizable: ReadConcern = new ReadConcern(JReadConcern.LINEARIZABLE)
  val Available: ReadConcern    = new ReadConcern(JReadConcern.AVAILABLE)

  val config: Config[ReadConcern] = Level.config.nested("level").optional.map(ReadConcern(_))

}
