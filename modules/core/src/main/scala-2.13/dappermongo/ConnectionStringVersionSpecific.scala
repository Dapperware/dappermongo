package dappermongo

import scala.jdk.CollectionConverters._

private[dappermongo] trait ConnectionStringVersionSpecific {
  protected[this] def inner: com.mongodb.ConnectionString

  def hosts: List[String] = inner.getHosts.asScala.toList

}
