package dappermongo.internal

import scala.jdk.CollectionConverters.*

private[dappermongo] trait CollectionConversionsVersionSpecific {

  def seqAsJava[T](seq: Seq[T]): java.util.List[T] =
    seq.asJava

  def listAsScala[T](list: java.util.List[T]): Seq[T] =
    list.asScala.toSeq

  def iterableAsScala[T](iterable: java.lang.Iterable[T]): Iterable[T] =
    iterable.asScala

  def collectionAsJava[T](collection: Iterable[T]): java.util.Collection[T] =
    collection.asJavaCollection

}
