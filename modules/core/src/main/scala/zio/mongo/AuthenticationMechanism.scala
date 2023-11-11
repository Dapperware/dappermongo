package zio.mongo

import zio.Config
import zio.Config.Secret

sealed trait AuthenticationMechanism extends Product with Serializable

object AuthenticationMechanism {

  case class GSSApi(userName: String)                                        extends AuthenticationMechanism
  case class Aws(userName: Option[String], password: Option[Secret])         extends AuthenticationMechanism
  case class X509(userName: String)                                          extends AuthenticationMechanism
  case class Plain(username: String, source: String, password: Secret)       extends AuthenticationMechanism
  case class ScramSha1(userName: String, source: String, password: Secret)   extends AuthenticationMechanism
  case class ScramSha256(userName: String, source: String, password: Secret) extends AuthenticationMechanism

  val config: Config[AuthenticationMechanism] = Config.succeed(GSSApi(""))
}
