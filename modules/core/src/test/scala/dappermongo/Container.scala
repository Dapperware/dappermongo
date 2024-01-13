package dappermongo

import com.dimafeng.testcontainers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import zio.{Config, Scope, ULayer, ZIO, ZLayer}

object Container {

  private case class Settings(ci: Boolean, version: String)

  private object Settings {
    val config: Config[Settings] = (
      Config.boolean("ci").withDefault(false) zip
        Config.string("version").withDefault("6.0.2")
    ).map((Settings.apply _).tupled)
  }

  val settings: ZLayer[MongoDBContainer, Nothing, MongoSettings] = ZLayer.scoped(for {
    container <- ZIO.service[MongoDBContainer]
    settings   = MongoSettings(Some(ConnectionString.unsafe(container.replicaSetUrl)))
  } yield settings)

  def make(version: String): ZIO[Scope, Nothing, MongoDBContainer] = ZIO
    .fromAutoCloseable(ZIO.succeed(MongoDBContainer(DockerImageName.parse(s"mongo:$version"))))
    .tap(container => ZIO.succeed(container.start))

  /**
   * A layer that is backed by a dockerized mongo instance using test containers
   */
  val testContainer: ZLayer[Any, Config.Error, MongoDBContainer] =
    ZLayer.scoped(ZIO.config(Settings.config).map(_.version).flatMap(make))

  /**
   * A layer that is backed by a local mongo instance.
   */
  val ci: ULayer[MongoSettings] =
    ZLayer.succeed(MongoSettings(Some(ConnectionString.unsafe("mongodb://localhost:27017"))))

  val live: ZLayer[Any, Config.Error, MongoSettings] = ZLayer(for {
    c <- ZIO.config(Settings.config)
  } yield if (c.ci) ci else (testContainer >>> settings)).flatten

}
