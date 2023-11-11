package zio.mongo

import com.mongodb.client.model.{
  Collation => JCollation,
  CollationStrength => JCollationStrength,
  CollationCaseFirst => JCollationCaseFirst,
  CollationAlternate => JCollationAlternate,
  CollationMaxVariable => JCollationMaxVariable
}

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
    builder.locale(locale.orNull)
    builder.caseLevel(caseLevel.fold[java.lang.Boolean](null)(java.lang.Boolean.valueOf))
    builder.collationCaseFirst(caseFirst.map(_.value).orNull)
    builder.collationStrength(strength.map(s => JCollationStrength.fromInt(s.value)).orNull)
    builder.numericOrdering(numericOrdering.fold(null: java.lang.Boolean)(java.lang.Boolean.valueOf))
    builder.collationAlternate(alternate.fold(null: JCollationAlternate)(_.value))
    builder.collationMaxVariable(maxVariable.fold(null: JCollationMaxVariable)(_.value))
    builder.backwards(backwards.map(java.lang.Boolean.valueOf).orNull)
    builder.normalization(normalization.map(java.lang.Boolean.valueOf).orNull)
    builder.build()
  }
}

object Collation {
  sealed abstract class CaseFirst(private[mongo] val value: JCollationCaseFirst)
  object CaseFirst {
    case object Upper extends CaseFirst(JCollationCaseFirst.UPPER)
    case object Lower extends CaseFirst(JCollationCaseFirst.LOWER)
    case object Off   extends CaseFirst(JCollationCaseFirst.OFF)
  }

  sealed abstract class Strength(val value: Int)
  object Strength {
    case object Primary    extends Strength(1)
    case object Secondary  extends Strength(2)
    case object Tertiary   extends Strength(3)
    case object Quaternary extends Strength(4)
    case object Identical  extends Strength(5)
  }

  sealed abstract class Alternate(private[mongo] val value: JCollationAlternate)
  object Alternate {
    case object NonIgnorable extends Alternate(JCollationAlternate.NON_IGNORABLE)
    case object Shifted      extends Alternate(JCollationAlternate.SHIFTED)
  }

  sealed abstract class MaxVariable(private[mongo] val value: JCollationMaxVariable)
  object MaxVariable {
    case object Punct extends MaxVariable(JCollationMaxVariable.PUNCT)
    case object Space extends MaxVariable(JCollationMaxVariable.SPACE)
  }
}
