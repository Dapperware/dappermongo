package dappermongo.aggregate

import scala.annotation.implicitAmbiguous

class CanFollow[T <: Stage]

object CanFollow extends CanFollowLowPriorityImplicits {

  @implicitAmbiguous("${T} must be the first stage in a pipeline")
  implicit def cannotFollowEvidence1[T <: Stage: CannotFollow]: CanFollow[T] = null
  implicit def cannotFollowEvidence2[T <: Stage: CannotFollow]: CanFollow[T] = null

}

trait CanFollowLowPriorityImplicits {

  implicit def canFollowEvidence[T <: Stage]: CanFollow[T] = new CanFollow[T]

}

/**
 * Defined for types which cannot follow any other stage in a pipeline. By
 * default all stages may come in any position in the pipeline.
 */
class CannotFollow[T <: Stage]
