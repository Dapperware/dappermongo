package zio.mongo

import scala.util.Try

import com.mongodb.{ConnectionString => JConnectionString}
import zio.Config.Secret
import zio.{Chunk, Config}

class ConnectionString private (protected val inner: JConnectionString) extends ConnectionStringVersionSpecific {
  private[mongo] def asJava: JConnectionString = inner

  def password: Option[Secret] = Option(inner.getPassword).map(pass => Secret(Chunk.fromArray(pass)))

  def isSrv: Boolean = inner.isSrvProtocol

  def srvMaxHosts: Option[Int] = Option(inner.getSrvMaxHosts)

  def srvServiceName: Option[String] = Option(inner.getSrvServiceName)

  def database: Option[String] = Option(inner.getDatabase)

  def collection: Option[String] = Option(inner.getCollection)

  def isDirectConnection: Option[Boolean] = Option(inner.isDirectConnection)

  def isLoadBalanced: Option[Boolean] = Option(inner.isLoadBalanced)

  def readPreference: Option[ReadPreference] = Option(inner.getReadPreference).map(ReadPreference(_))

  def readConcern: Option[ReadConcern] = Option(inner.getReadConcern).map(ReadConcern(_))

  def writeConcern: Option[WriteConcern] = Option(inner.getWriteConcern).map(WriteConcern(_))

  def retryWrites: Option[Boolean] = Option(inner.getRetryWritesValue)

  def applicationName: Option[String] = Option(inner.getApplicationName)

}

object ConnectionString {

  private[mongo] def unsafe(uri: String): ConnectionString = new ConnectionString(new JConnectionString(uri))

  def parse(uri: String): Try[ConnectionString] = Try(unsafe(uri))

  val config: Config[ConnectionString] =
    Config.string.mapAttempt(unsafe)
}
