package dappermongo.aggregate

import scala.annotation.implicitNotFound

@implicitNotFound("${T} must be the first stage in a pipeline")
class Combiner[T <: Stage]
