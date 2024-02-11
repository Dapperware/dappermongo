package dappermongo.internal

import scala.util.Try

import reactivemongo.api.bson.BSONDocument

private[dappermongo] class DocumentEncodedFn(fn: => Try[BSONDocument]) extends (() => Try[BSONDocument]) {
  override def apply(): Try[BSONDocument] = fn
}

object DocumentEncodedFn {
  def apply(fn: => Try[BSONDocument]): DocumentEncodedFn = new DocumentEncodedFn(fn)
}
