package dappermongo.aggregate.search

import dappermongo.aggregate.search.SearchOperator.AutoComplete.{Fuzzy, TokenOrder}
import reactivemongo.api.bson.{BSONDocument, BSONDocumentWriter, BSONString, BSONValue, BSONWriter, document}

import reactivemongo.api.bson.collectionWriter

sealed trait SearchOperator

object SearchOperator {

  case class AutoComplete(
    query: List[String],
    path: SearchPath,
    fuzzy: Option[Fuzzy] = None,
    score: Option[Score] = None,
    tokenOrder: Option[TokenOrder] = None
  ) extends SearchOperator

  object AutoComplete {
    case class Fuzzy(maxEdits: Int, prefixLength: Int, maxExpansions: Int)

    object Fuzzy {
      implicit lazy val writer: BSONDocumentWriter[Fuzzy] = BSONDocumentWriter[Fuzzy] { fuzzy =>
        document(
          "maxEdits"      -> fuzzy.maxEdits,
          "prefixLength"  -> fuzzy.prefixLength,
          "maxExpansions" -> fuzzy.maxExpansions
        )
      }
    }

    sealed trait TokenOrder

    object TokenOrder {
      case object Any        extends TokenOrder
      case object Sequential extends TokenOrder

      implicit lazy val writer: BSONWriter[TokenOrder] = BSONWriter[TokenOrder] {
        case Any        => BSONString("any")
        case Sequential => BSONString("sequential")
      }
    }
  }
  case class Compound(
    must: List[SearchOperator],
    should: List[SearchOperator],
    mustNot: List[SearchOperator],
    filter: List[SearchOperator]
  ) extends SearchOperator

  case class Phrase(
    query: String,
    path: SearchPath,
    slop: Option[Int] = None,
    score: Option[Score] = None,
    tokenOrder: Option[TokenOrder] = None
  ) extends SearchOperator

  case class EmbeddedDocument(
    operator: SearchOperator,
    path: SearchPath,
    score: Option[Score] = None
  ) extends SearchOperator

  case class Equals(
    value: BSONValue,
    path: SearchPath,
    score: Option[Score] = None
  ) extends SearchOperator

  case class Exists(
    path: SearchPath,
    score: Option[Score] = None
  ) extends SearchOperator

  case class Facet(
    facets: Map[String, Facet.Named],
    operator: Option[SearchOperator] = None
  ) extends SearchOperator

  object Facet {
    case class Named(
      `type`: String,
      path: String,
      numBuckets: Option[Int] = None
    )

    object Named {

      implicit lazy val writer: BSONDocumentWriter[Named] = BSONDocumentWriter[Named] { facet =>
        document(
          "type"       -> facet.`type`,
          "path"       -> facet.path,
          "numBuckets" -> facet.numBuckets
        )
      }
    }
  }

  implicit lazy val writer: BSONDocumentWriter[SearchOperator] =
    BSONDocumentWriter[SearchOperator](toBSONDocument)

  private def toBSONDocument(operator: SearchOperator): BSONDocument = operator match {
    case Compound(must, should, mustNot, filter) =>
      document(
        "must"    -> must.map(toBSONDocument),
        "should"  -> should.map(toBSONDocument),
        "mustNot" -> mustNot.map(toBSONDocument),
        "filter"  -> filter.map(toBSONDocument)
      )
    case Phrase(query, path, slop, score, tokenOrder) =>
      document(
        "query"      -> query,
        "path"       -> path,
        "slop"       -> slop,
        "score"      -> score,
        "tokenOrder" -> tokenOrder
      )
    case EmbeddedDocument(operator, path, score) =>
      document(
        "operator" -> toBSONDocument(operator),
        "path"     -> path,
        "score"    -> score
      )
    case Equals(value, path, score) =>
      document(
        "value" -> value,
        "path"  -> path,
        "score" -> score
      )
    case Exists(path, score) =>
      document(
        "path"  -> path,
        "score" -> score
      )
    case Facet(facets, operator) =>
      document(
        "facets"   -> facets,
        "operator" -> operator.map(toBSONDocument)
      )
    case AutoComplete(query, path, fuzzy, score, tokenOrder) =>
      document(
        "query"      -> query,
        "path"       -> path,
        "fuzzy"      -> fuzzy,
        "score"      -> score,
        "tokenOrder" -> tokenOrder
      )
  }
}
