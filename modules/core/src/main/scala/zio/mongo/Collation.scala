package zio.mongo

import com.mongodb.client.model.{Collation => JCollation}

case class Collation(
  locale: Option[String] = None,
  caseLevel: Option[Boolean] = None,
  caseFirst: Option[Collation.CaseFirst] = None,
  strength: Option[Collation.Strength] = None,
  numericOrdering: Option[Boolean] = None,
  alternate: Option[Collation.Alternate] = None,
  maxVariable: Option[Collation.MaxVariable] = None,
  backwards: Option[Boolean] = None,
  normalization: Option[Boolean] = None
) {
  def asJava: JCollation = {
    val builder = JCollation.builder()
//    builder.locale(locale.orNull)
//    builder.caseLevel(caseLevel.orNull)
//    builder.collationCaseFirst(caseFirst.map(_.toString).orNull)
//    builder.collationStrength(strength.map(_.value).orNull)
//    builder.numericOrdering(numericOrdering.orNull)
//    builder.collationAlternate(alternate.map(_.toString).orNull)
//    builder.collationMaxVariable(maxVariable.map(_.toString).orNull)
//    builder.backwards(backwards.orNull)
//    builder.normalization(normalization.orNull)
    builder.build()
  }
}

object Collation {
  sealed trait CaseFirst
  object CaseFirst {
    case object Upper extends CaseFirst
    case object Lower extends CaseFirst
    case object Off   extends CaseFirst
  }

  sealed abstract class Strength private (val value: Int)
  object Strength {
    case object Primary    extends Strength(1)
    case object Secondary  extends Strength(2)
    case object Tertiary   extends Strength(3)
    case object Quaternary extends Strength(4)
    case object Identical  extends Strength(5)
  }

  sealed trait Alternate
  object Alternate {
    case object NonIgnorable extends Alternate
    case object Shifted      extends Alternate
  }

  sealed trait MaxVariable
  object MaxVariable {
    case object Punct extends MaxVariable
    case object Space extends MaxVariable
  }
}
