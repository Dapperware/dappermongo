package dappermongo

import scala.jdk.CollectionConverters._

import com.mongodb
import com.mongodb.{ReadPreferenceHedgeOptions, TaggableReadPreference}
import dappermongo.ReadPreference.Taggable.applySettings
import java.util.concurrent.TimeUnit
import zio.{Chunk, Config, Duration}

sealed abstract class ReadPreference(private[dappermongo] val wrapped: com.mongodb.ReadPreference) {

  def isSecondaryOk: Boolean = wrapped.isSecondaryOk

  def name: String = wrapped.getName

}

object ReadPreference {

  private[dappermongo] def apply(inner: com.mongodb.ReadPreference): ReadPreference = inner match {
    case rp: TaggableReadPreference =>
      val maxStaleness = Option(rp.getMaxStaleness(TimeUnit.MILLISECONDS)).map(Duration.fromMillis(_))
      val tags = Option(rp.getTagSetList).map(ts =>
        Chunk.fromIterable(ts.asScala.map(ts => TagSet(ts.asScala.map(t => t.getName -> t.getValue).toMap)))
      )
      val hedgeOptions = Option(rp.getHedgeOptions).map(ho => HedgeOptions(ho.isEnabled))

      inner.getName match {
        case "primaryPreferred" =>
          PrimaryPreferred(maxStaleness, tags, hedgeOptions)
        case "secondary" =>
          Secondary(maxStaleness, tags, hedgeOptions)
        case "secondaryPreferred" =>
          SecondaryPreferred(maxStaleness, tags, hedgeOptions)
        case "nearest" =>
          Nearest(maxStaleness, tags, hedgeOptions)
        case _ =>
          new Taggable(inner, maxStaleness, tags, hedgeOptions) {}
      }
    case _ if inner.getName == "primary" => Primary
    case other                           => new ReadPreference(other) {}
  }

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
    ): mongodb.ReadPreference = {
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
    maxStaleness: Option[Duration] = None,
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

    val taggable = Config.duration("maxStaleness").optional zip
      TagSet.config.nested("tags").repeat.optional.map(_.filter(_.exists(_.tagSet.nonEmpty))) zip
      Config.boolean("hedge").map(HedgeOptions.apply).optional

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
