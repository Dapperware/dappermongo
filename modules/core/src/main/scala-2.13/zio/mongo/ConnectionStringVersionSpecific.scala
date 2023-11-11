package zio.mongo

import scala.jdk.CollectionConverters._

private[mongo] trait ConnectionStringVersionSpecific {
  protected[this] def inner: com.mongodb.ConnectionString

  def hosts: List[String] = inner.getHosts.asScala.toList

}
