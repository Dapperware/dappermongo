package zio.mongo

import com.dimafeng.testcontainers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import zio.{Config, Scope, ULayer, ZIO, ZLayer}

object Container {

  private case class Settings(ci: Boolean, version: String)

  object Settings {
    val config: Config[Settings] = (
      Config.boolean("ci") zip
        Config.string("version")
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
  val testContainer: ZLayer[Any, Nothing, MongoDBContainer] =
    ZLayer.scoped(ZIO.config(Settings.config).map(_.version).flatMap(make))

  /**
   * A layer that is backed by a local mongo instance.
   */
  val ci: ULayer[MongoSettings] =
    ZLayer.succeed(MongoSettings(Some(ConnectionString.unsafe("mongodb://localhost:27017"))))

  val live: ZLayer[Any, Config.Error, Nothing] = ZLayer(for {
    c <- ZIO.config(Settings.config)
  } yield if (c.ci) ci else (testContainer >>> settings)).flatten

}
