package dappermongo.aggregate

import dappermongo.aggregate.search.{SearchOperator, SearchPath}
import reactivemongo.api.bson.{
  BSON,
  BSONArray,
  BSONDocument,
  BSONDocumentWriter,
  BSONInteger,
  BSONString,
  BSONValue,
  BSONWriter,
  document
}
import zio.NonEmptyChunk

import scala.util.Try

/**
 * A stage in the aggregation pipeline. Most stages can be combined with other
 * stages in any order, but some stages will be required to be the first stage
 * in a pipeline.
 */
sealed trait Stage extends Product with Serializable { self =>

  def >>>[T <: Stage](that: T)(implicit ev: CanFollow[T]): Pipeline = {
    implicit val _ = ev // To satisfy scalafix
    Pipeline(NonEmptyChunk(self, that))
  }

  protected def toDocument: Try[BSONDocument]

}

object Stage {

  implicit def toPipeline[T <: Stage](stage: T): Pipeline = Pipeline(NonEmptyChunk(stage))

  def addFields[T: BSONDocumentWriter](fields: T): AddFields[T] =
    AddFields(fields, implicitly)

  def bucket(
    groupBy: String,
    boundaries: BSONArray,
    default: Option[BSONValue],
    output: BSONDocument
  ): Bucket =
    Bucket(groupBy, boundaries, default, output)

  def bucketAuto(
    groupBy: String,
    buckets: Int
  ): BucketAuto =
    BucketAuto(groupBy, buckets)

  def facet(
    facets: Map[String, Pipeline]
  ): Facet =
    Facet(facets)

  def facet(
    facet: (String, Pipeline),
    facets: (String, Pipeline)*
  ): Facet =
    Facet((facet +: facets).toMap)
  def `match`[T: BSONDocumentWriter](filter: T): Match[T] =
    Match(filter, implicitly)

  def group[T: BSONWriter, U: BSONDocumentWriter](id: T, fields: U): Group[T, U] =
    Group(id, fields, implicitly, implicitly)

  def limit(limit: Int): Limit =
    Limit(limit)

  def project[T: BSONDocumentWriter](fields: T): Project[T] =
    Project(fields, implicitly)

  def skip(skip: Int): Skip =
    Skip(skip)

  def sortByCount[B: BSONWriter](path: B): SortByCount[B] =
    SortByCount(path, implicitly)

  def unwind(path: String): Unwind =
    Unwind(path)

  def sort[T: BSONDocumentWriter](sort: T): Sort[T] =
    Sort(sort, implicitly)

  def search(
    operatorOrCollector: SearchOperator,
    concurrent: Option[Boolean] = None,
    highlight: Option[Search.HighlightOptions] = None,
    sort: Option[Map[String, Search.SortOrder]] = None,
    count: Option[Search.CountOptions] = None,
    returnStoredSource: Boolean = false,
    tracking: Option[Search.TrackingOptions] = None,
    scoreDetails: Option[Boolean] = None,
    index: Option[String] = None,
    from: Option[Search.From] = None
  ): Search =
    Search(
      operatorOrCollector,
      concurrent,
      highlight,
      sort,
      count,
      returnStoredSource,
      tracking,
      scoreDetails,
      index,
      from
    )

  final case class AddFields[T](fields: T, writer: BSONDocumentWriter[T]) extends Stage { self =>
    override def toDocument: Try[BSONDocument] =
      AddFields.writer.writeTry(self)
  }

  object AddFields {

    implicit def writer[T]: BSONDocumentWriter[AddFields[T]] =
      BSONDocumentWriter.from[AddFields[T]] { addFields =>
        BSON
          .write(addFields.fields)(addFields.writer)
          .map(operator => document("$addFields" -> operator))
      }

  }

  final case class Bucket(
    groupBy: String,
    boundaries: BSONArray,
    default: Option[BSONValue],
    output: BSONDocument
  ) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Bucket.writer.writeTry(this)
  }

  object Bucket {
    implicit def writer: BSONDocumentWriter[Bucket] =
      BSONDocumentWriter[Bucket] { bucket =>
        document(
          "$bucket" -> document(
            "groupBy"    -> bucket.groupBy,
            "boundaries" -> bucket.boundaries,
            "default"    -> bucket.default,
            "output"     -> bucket.output
          )
        )
      }
  }

  final case class BucketAuto(
    groupBy: String,
    buckets: Int
  ) extends Stage {
    override def toDocument: Try[BSONDocument] =
      BucketAuto.writer.writeTry(this)
  }

  object BucketAuto {
    implicit def writer: BSONDocumentWriter[BucketAuto] =
      BSONDocumentWriter[BucketAuto] { bucketAuto =>
        document(
          "$bucketAuto" -> document(
            "groupBy" -> bucketAuto.groupBy,
            "buckets" -> bucketAuto.buckets
          )
        )
      }
  }

  final case class Facet(facets: Map[String, Pipeline]) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Facet.writer.writeTry(this)
  }

  object Facet {
    implicit def writer: BSONDocumentWriter[Facet] =
      BSONDocumentWriter[Facet] { facet =>
        val facets = facet.facets.map { case (name, pipeline) =>
          name -> pipeline.stages.map(_.toDocument.get)
        }
        document("$facet" -> facets)
      }
  }

  final case class Match[T](filter: T, writer: BSONDocumentWriter[T]) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Match.writer.writeTry(this)
  }

  object Match {

    implicit def writer[T]: BSONDocumentWriter[Match[T]] =
      BSONDocumentWriter.from[Match[T]] { matchStage =>
        BSON
          .write(matchStage.filter)(matchStage.writer)
          .map(operator => document("$match" -> operator))
      }

  }

  final case class Group[T, U](id: T, fields: U, writer: BSONWriter[T], fieldWriter: BSONDocumentWriter[U])
      extends Stage {
    override def toDocument: Try[BSONDocument] =
      Group.writer.writeTry(this)
  }

  object Group {
    implicit def writer[T, U]: BSONDocumentWriter[Group[T, U]] =
      BSONDocumentWriter.from[Group[T, U]] { group =>
        for {
          idOperator     <- BSON.write(group.id)(group.writer)
          fieldsOperator <- BSON.writeDocument(group.fields)(group.fieldWriter)
        } yield document("$group" -> (document("_id" -> idOperator) ++ fieldsOperator))
      }
  }

  final case class Project[T](fields: T, writer: BSONDocumentWriter[T]) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Project.writer.writeTry(this)
  }

  object Project {

    implicit def writer[T]: BSONDocumentWriter[Project[T]] =
      BSONDocumentWriter.from[Project[T]] { project =>
        BSON
          .write(project.fields)(project.writer)
          .map(operator => document("$project" -> operator))
      }

  }

  final case class Limit(limit: Int) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Limit.writer.writeTry(this)
  }

  object Limit {

    implicit def writer: BSONDocumentWriter[Limit] =
      BSONDocumentWriter[Limit] { limit =>
        document("$limit" -> limit.limit)
      }

  }

  final case class Skip(skip: Int) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Skip.writer.writeTry(this)
  }

  object Skip {

    implicit def writer: BSONDocumentWriter[Skip] =
      BSONDocumentWriter[Skip] { skip =>
        document("$skip" -> skip.skip)
      }

    implicit val combiner: CanFollow[Skip] = new CanFollow[Skip]
  }

  final case class SortByCount[B](path: B, writer: BSONWriter[B]) extends Stage {
    override def toDocument: Try[BSONDocument] =
      SortByCount.writer.writeTry(this)
  }

  object SortByCount {
    implicit def writer[B]: BSONDocumentWriter[SortByCount[B]] =
      BSONDocumentWriter.from[SortByCount[B]] { sortByCount =>
        BSON.write(sortByCount.path)(sortByCount.writer).map { path =>
          document("$sortByCount" -> path)
        }
      }
  }

  final case class Unwind(path: String) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Unwind.writer.writeTry(this)
  }

  object Unwind {

    implicit def writer: BSONDocumentWriter[Unwind] =
      BSONDocumentWriter[Unwind] { unwind =>
        document("$unwind" -> unwind.path)
      }

  }

  final case class Sort[T](sort: T, writer: BSONDocumentWriter[T]) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Sort.writer.writeTry(this)
  }

  object Sort {

    implicit def writer[T]: BSONDocumentWriter[Sort[T]] =
      BSONDocumentWriter.from[Sort[T]] { sort =>
        BSON
          .write(sort.sort)(sort.writer)
          .map(operator => document("$sort" -> operator))
      }

  }

  final case class Search(
    operatorOrCollector: SearchOperator,
    concurrent: Option[Boolean] = None,
    highlight: Option[Search.HighlightOptions] = None,
    sort: Option[Map[String, Search.SortOrder]] = None,
    count: Option[Search.CountOptions] = None,
    returnStoredSource: Boolean = false,
    tracking: Option[Search.TrackingOptions] = None,
    scoreDetails: Option[Boolean] = None,
    index: Option[String] = None,
    from: Option[Search.From] = None
  ) extends Stage {
    override def toDocument: Try[BSONDocument] =
      Search.writer.writeTry(this)
  }

  object Search {
    implicit val cannotFollow: CannotFollow[Search] = new CannotFollow[Search]

    case class HighlightOptions(
      path: String,
      maxCharsToExamine: Option[Int] = None,
      maxNumPassages: Option[Int] = None
    )

    sealed trait SortOrder

    object SortOrder {
      case object Ascending  extends SortOrder
      case object Descending extends SortOrder
    }

    case class CountOptions(
      `type`: CountType,
      threshold: Option[Int] = None
    )

    sealed trait CountType

    object CountType {
      case object Total      extends CountType
      case object LowerBound extends CountType

      implicit val writer: BSONWriter[CountType] = BSONWriter[CountType] {
        case Total      => BSONString("total")
        case LowerBound => BSONString("lower_bound")
      }
    }

    case class TrackingOptions(
      searchTerms: String
    )

    sealed trait From

    object From {
      case class Before(value: String) extends From
      case class After(value: String)  extends From
    }

    implicit val writer: BSONDocumentWriter[Search] = BSONDocumentWriter.from[Search] { search =>
      val operator = BSON.write(search.operatorOrCollector)
      val options = List(
        search.concurrent.map(cc => document("concurrent" -> cc)),
        search.highlight.map { highlight =>
          document(
            "highlight" -> document(
              "path"              -> highlight.path,
              "maxCharsToExamine" -> highlight.maxCharsToExamine,
              "maxNumPassages"    -> highlight.maxNumPassages
            )
          )
        },
        search.sort.map { sort =>
          document(
            "sort" -> BSONDocument(
              sort.map { case (path, order) =>
                path -> (order match {
                  case SortOrder.Ascending  => BSONInteger(1)
                  case SortOrder.Descending => BSONInteger(-1)
                })
              }
            )
          )
        },
        search.count.map { count =>
          document(
            "count" -> document(
              "type"      -> count.`type`,
              "threshold" -> count.threshold
            )
          )
        },
        Some(document("returnStoredSource" -> search.returnStoredSource)),
        search.tracking.map { tracking =>
          document(
            "tracking" -> document(
              "searchTerms" -> tracking.searchTerms
            )
          )
        },
        search.scoreDetails.map(sd => document("scoreDetails" -> sd)),
        search.index.map(idx => document("index" -> idx)),
        search.from.map {
          case From.Before(value) => document("from" -> BSONString(s"before:$value"))
          case From.After(value)  => document("from" -> BSONString(s"after:$value"))
        }
      ).flatten

      operator.map { op =>
        val ds = options.foldLeft(document("compound" -> op)) { (doc, opt) =>
          doc ++ opt
        }

        document("$search" -> ds)
      }
    }
  }

  implicit val writer: BSONDocumentWriter[Stage] = BSONDocumentWriter.from[Stage] {
    _.toDocument
  }

}
