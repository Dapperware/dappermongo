package dappermongo

import com.mongodb.MongoCredential
import zio.Config

class Credential private (private[dappermongo] val wrapped: MongoCredential) {}

object Credential {
  val config: Config[Credential] = AuthenticationMechanism.config.map {
    case AuthenticationMechanism.GSSApi(userName) => MongoCredential.createGSSAPICredential(userName)
    case AuthenticationMechanism.Aws(userName, password) =>
      MongoCredential.createAwsCredential(userName.orNull, password.map(_.value.toArray).orNull)
    case AuthenticationMechanism.X509(userName) =>
      MongoCredential.createMongoX509Credential(userName)
    case AuthenticationMechanism.Plain(username, source, password) =>
      MongoCredential.createPlainCredential(username, source, password.value.toArray)
    case AuthenticationMechanism.ScramSha1(userName, source, password) =>
      MongoCredential.createScramSha1Credential(userName, source, password.value.toArray)
    case AuthenticationMechanism.ScramSha256(userName, source, password) =>
      MongoCredential.createScramSha256Credential(userName, source, password.value.toArray)
  }.map(new Credential(_))
}
