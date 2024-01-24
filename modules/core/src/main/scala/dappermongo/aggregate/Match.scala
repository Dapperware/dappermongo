package dappermongo.aggregate

import reactivemongo.api.bson.{BSON, BSONDocument, BSONDocumentWriter, document}
import zio.Chunk

sealed trait Stage extends Product with Serializable { self =>

  def ++[T <: Stage](that: T)(implicit ev: Combiner[T]): Pipeline =
    Pipeline(self, Chunk.single(that))

}

object Stage {

  def `match`[T: BSONDocumentWriter](filter: T): Match[T] =
    Match(filter, implicitly)

  def addFields[T: BSONDocumentWriter](fields: T): AddFields[T] =
    AddFields(fields, implicitly)

  def limit(limit: Int): Limit =
    Limit(limit)

  def project[T: BSONDocumentWriter](fields: T): Project[T] =
    Project(fields, implicitly)

  case class AddFields[T](fields: T, writer: BSONDocumentWriter[T]) extends Stage

  case class Match[T](filter: T, writer: BSONDocumentWriter[T]) extends Stage

  case class Project[T](fields: T, writer: BSONDocumentWriter[T]) extends Stage

  object Project {

    implicit def writer[T]: BSONDocumentWriter[Project[T]] =
      BSONDocumentWriter.from[Project[T]] { project =>
        BSON
          .write(project.fields)(project.writer)
          .map(operator => document("$project" -> operator))
      }

    implicit def combiner[T]: Combiner[Project[T]] = new Combiner[Project[T]]
  }

  case class Limit(limit: Int) extends Stage

  object Limit {

    implicit def writer: BSONDocumentWriter[Limit] =
      BSONDocumentWriter[Limit] { limit =>
        document("$limit" -> limit.limit)
      }

    implicit val combiner: Combiner[Limit] = new Combiner[Limit]
  }

  case class Skip(skip: Int) extends Stage

  object Match {

    implicit def writer[T]: BSONDocumentWriter[Match[T]] =
      BSONDocumentWriter.from[Match[T]] { matchStage =>
        BSON
          .write(matchStage.filter)(matchStage.writer)
          .map(operator => document("$match" -> operator))
      }

    implicit def combiner[T]: Combiner[Match[T]] = new Combiner[Match[T]]
  }

}

object Example {

  import Stage._

  val s1 = (`match`(document("name" -> "John")) ++
    limit(1))

  val s2 = project[BSONDocument](
    document("name" -> 1, "age" -> 1)
  )

  s1 ++ s2

}
