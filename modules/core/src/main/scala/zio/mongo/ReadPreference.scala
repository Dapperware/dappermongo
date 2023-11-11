package zio.mongo

import scala.jdk.CollectionConverters._
import com.mongodb.ReadPreferenceHedgeOptions

import java.util.concurrent.TimeUnit
import zio.mongo.ReadPreference.Taggable.applySettings
import zio.{Chunk, Config, Duration, mongo}

sealed abstract class ReadPreference(private[mongo] val wrapped: com.mongodb.ReadPreference) {

  def isSecondaryOk: Boolean = wrapped.isSecondaryOk

  def name: String = wrapped.getName

}

object ReadPreference {

  private[mongo] def apply(inner: com.mongodb.ReadPreference): ReadPreference = new ReadPreference(inner) {}

  abstract class Taggable(
    wrapped: com.mongodb.ReadPreference,
    maxStaleness: Option[Duration],
    tags: Option[Chunk[TagSet]],
    hedgeOptions: Option[HedgeOptions]
  ) extends ReadPreference(applySettings(wrapped, maxStaleness, tags, hedgeOptions))

  object Taggable {
    def applySettings(
      wrapped: com.mongodb.ReadPreference,
      maxStaleness: Option[Duration],
      tags: Option[Chunk[TagSet]],
      hedgeOptions: Option[HedgeOptions]
    ) = {
      val rp = wrapped
        .withMaxStalenessMS(maxStaleness.map(ms => java.lang.Long.valueOf(ms.toMillis)).orNull, TimeUnit.MILLISECONDS)

      hedgeOptions.foldLeft(tags.fold(rp)(ts => rp.withTagSetList(ts.map(_.asJava).asJava)))((rp, ho) =>
        rp.withHedgeOptions(ReadPreferenceHedgeOptions.builder().enabled(ho.enabled).build())
      )
    }

  }

  case object Primary extends ReadPreference(com.mongodb.ReadPreference.primary())
  case class SecondaryPreferred(
    maxStaleness: Option[Duration] = None,
    tags: Option[Chunk[TagSet]] = None,
    hedgeOptions: Option[HedgeOptions] = None
  ) extends Taggable(com.mongodb.ReadPreference.secondaryPreferred(), maxStaleness, tags, hedgeOptions)
  case class Secondary(
    maxStaleness: Option[Duration],
    tags: Option[Chunk[TagSet]] = None,
    hedgeOptions: Option[HedgeOptions] = None
  ) extends Taggable(com.mongodb.ReadPreference.secondary(), maxStaleness, tags, hedgeOptions)
  case class PrimaryPreferred(
    maxStaleness: Option[Duration],
    tags: Option[Chunk[TagSet]] = None,
    hedgeOptions: Option[HedgeOptions] = None
  ) extends Taggable(
        com.mongodb.ReadPreference.primaryPreferred(),
        maxStaleness,
        tags,
        hedgeOptions
      )
  case class Nearest(
    maxStaleness: Option[Duration] = None,
    tags: Option[Chunk[TagSet]] = None,
    hedgeOptions: Option[HedgeOptions] = None
  ) extends Taggable(com.mongodb.ReadPreference.nearest(), maxStaleness, tags, hedgeOptions)

  final case class HedgeOptions(
    enabled: Boolean
  )

  val config: Config[ReadPreference] = {
    val nameOnly = Config.string.switch(
      "primary"            -> Config.succeed(Primary),
      "secondaryPreferred" -> Config.succeed(SecondaryPreferred()),
      "secondary"          -> Config.succeed(Secondary(None)),
      "primaryPreferred"   -> Config.succeed(PrimaryPreferred(None)),
      "nearest"            -> Config.succeed(Nearest())
    )

    val taggable = (Config.duration("maxStaleness").optional zip
      TagSet.config.nested("tags").repeat.optional zip
      Config.boolean("hedge").map(HedgeOptions).optional)

    val withParams = Config
      .string("name")
      .switch(
        "primary"            -> Config.succeed(Primary),
        "secondaryPreferred" -> taggable.map((SecondaryPreferred.apply _).tupled),
        "secondary"          -> taggable.map((Secondary.apply _).tupled),
        "primaryPreferred"   -> taggable.map((PrimaryPreferred.apply _).tupled),
        "nearest"            -> taggable.map((Nearest.apply _).tupled)
      )

    nameOnly orElse withParams
  }
}

case class TagSet(tagSet: Map[String, String]) {
  def asJava: com.mongodb.TagSet =
    new com.mongodb.TagSet(tagSet.view.map(kv => new com.mongodb.Tag(kv._1, kv._2)).toList.asJava)
}

object TagSet {
  val config: Config[TagSet] = Config.table(Config.string).map(TagSet.apply)
}
