package dappermongo.aggregate.search

import reactivemongo.api.bson.{BSONValue, BSONWriter, document}

sealed trait Score

object Score {
  case class Boost(
    value: Double,                   // Conditional
    path: String,                    // Conditional
    undefined: Option[Double] = None // Only specified with path
  ) extends Score

  case class Constant(
    value: Double
  ) extends Score

  case class Embedded(
    aggregate: Option[String],
    outerScore: Option[Score]
  ) extends Score

  case class Function(expressions: Function.Expression) extends Score

  object Function {
    sealed trait Expression

    object Expression {
      case class Arithmetic(
        add: List[Function.Expression],
        multiply: List[Function.Expression]
      ) extends Expression

      case class Constant(value: Double) extends Expression

      case class Gaussian(
        path: Path,
        origin: Double,
        scale: Double,
        offset: Option[Double] = None,
        decay: Option[Double] = None
      ) extends Expression

      case class Path(
        path: String,
        undefined: Option[Double] = None
      ) extends Expression

      object Path {
        implicit val writer: BSONWriter[Path] = BSONWriter[Path] { path =>
          document(
            "path"      -> path.path,
            "undefined" -> path.undefined
          )
        }
      }

      case object Score extends Expression

      case class Unary(
        log: Option[Function.Expression],
        log1p: Option[Function.Expression]
      ) extends Expression

      implicit val writer: BSONWriter[Expression] = BSONWriter[Expression](toBSONValue)

      private def toBSONValue(expression: Expression): BSONValue = expression match {
        case Arithmetic(add, multiply) =>
          document(
            "add"      -> add,
            "multiply" -> multiply
          )
        case Constant(value) =>
          document(
            "constant" -> value
          )
        case Gaussian(path, origin, scale, offset, decay) =>
          document(
            "gaussian" -> document(
              "path"   -> path,
              "origin" -> origin,
              "scale"  -> scale,
              "offset" -> offset,
              "decay"  -> decay
            )
          )
        case Path(path, undefined) =>
          document(
            "path"      -> path,
            "undefined" -> undefined
          )
        case Score =>
          document(
            "score" -> "relevence"
          )
        case Unary(log, log1p) =>
          document(
            "log"   -> log,
            "log1p" -> log1p
          )
      }

    }
  }

  implicit lazy val writer: BSONWriter[Score] = BSONWriter(toBSONValue)

  private def toBSONValue(score: Score): BSONValue = score match {
    case Boost(value, path, undefined) =>
      document(
        "boost"     -> value,
        "path"      -> path,
        "undefined" -> undefined
      )
    case Constant(value) =>
      document(
        "constant" -> value
      )
    case Embedded(aggregate, outerScore) =>
      document(
        "embedded" -> document(
          "aggregate"  -> aggregate,
          "outerScore" -> outerScore
        )
      )
    case Function(expression) =>
      document("function" -> expression)
  }
}
