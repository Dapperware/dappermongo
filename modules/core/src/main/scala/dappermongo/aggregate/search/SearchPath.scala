package dappermongo.aggregate.search

import zio.{Chunk, NonEmptyChunk}

case class SearchPath(paths: NonEmptyChunk[SearchPath.Node]) {
  def ++(that: SearchPath): SearchPath = copy(paths = paths ++ that.paths)

}
object SearchPath {

  def field(name: String): SearchPath = SearchPath(NonEmptyChunk.single(Node.Field(name)))

  def analyzer(value: String, analyzer: String): SearchPath =
    SearchPath(NonEmptyChunk.single(Node.Analyzer(value, analyzer)))

  def wildcard(value: String): SearchPath = SearchPath(NonEmptyChunk.single(Node.Wildcard(value)))

  sealed trait Node

  object Node {
    case class Field(name: String)                       extends Node
    case class Analyzer(value: String, analyzer: String) extends Node

    case class Wildcard(value: String) extends Node
  }
}
