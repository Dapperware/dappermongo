package zio.mongo

import com.dimafeng.testcontainers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import zio.{ZIO, ZLayer}

object Container {

  val settings = ZLayer.scoped(for {
    container <- ZIO.service[MongoDBContainer]
    settings   = MongoSettings(Some(ConnectionString.unsafe(container.replicaSetUrl)))
  } yield settings)

  val make = ZIO
    .fromAutoCloseable(ZIO.succeed(MongoDBContainer(DockerImageName.parse("mongo:6.0.1"))))
    .tap(container => ZIO.succeed(container.start))

  val live = ZLayer.scoped(make)

}
