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

  private val gssapi: Config[GSSApi] = Config.string("userName").map(GSSApi)
  private val aws: Config[Aws] =
    (Config.string("userName").optional zip Config.secret("password").optional).map((Aws.apply _).tupled)
  private val x509: Config[X509] = Config.string("userName").map(X509)
  private val plain: Config[Plain] =
    (Config.string("userName") zip Config.string("source") zip Config.secret("password")).map {
      (Plain.apply _).tupled
    }

  private val scramSha1: Config[ScramSha1] =
    (Config.string("userName") zip Config.string("source") zip Config.secret("password")).map {
      (ScramSha1.apply _).tupled
    }

  private val scramSha256: Config[ScramSha256] =
    (Config.string("userName") zip Config.string("source") zip Config.secret("password")).map {
      (ScramSha256.apply _).tupled
    }

  val config: Config[AuthenticationMechanism] = Config.defer(
    Config
      .string("authMechanism")
      .switch(
        "GSSAPI"        -> gssapi,
        "AWS"           -> aws,
        "X509"          -> x509,
        "PLAIN"         -> plain,
        "SCRAM-SHA-1"   -> scramSha1,
        "SCRAM-SHA-256" -> scramSha256
      )
  )
}
