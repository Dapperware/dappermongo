package dappermongo.aggregate.search

import reactivemongo.api.bson.{BSONString, BSONValue, BSONWriter, array, document}
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

  implicit val writer: BSONWriter[SearchPath] = BSONWriter[SearchPath] { path =>
    val head = path.paths.head
    val tail = path.paths.tail

    def writeNode(node: Node): BSONValue = node match {
      case Node.Field(name)               => BSONString(name)
      case Node.Analyzer(value, analyzer) => document("value" -> value, "analyzer" -> analyzer)
      case Node.Wildcard(value)           => document("wildcard" -> value)
    }

    tail match {
      case Chunk() => writeNode(head)
      case rest    => array(writeNode(head)) ++ array(rest.map(writeNode))
    }
  }
}
