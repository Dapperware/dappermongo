package dappermongo.internal

import scala.collection.JavaConverters._

private[dappermongo] trait CollectionConversionsVersionSpecific {

  def seqAsJava[T](seq: Seq[T]): java.util.List[T] =
    seq.asJava

  def listAsScala[T](list: java.util.List[T]): Seq[T] =
    list.asScala.toSeq

  def listAsJava[T](list: List[T]): java.util.List[T] =
    list.asJava

  def iterableAsScala[T](iterable: java.lang.Iterable[T]): Iterable[T] =
    iterable.asScala

  def collectionAsJava[T](collection: Iterable[T]): java.util.Collection[T] =
    collection.asJavaCollection

  def mapAsScala[T, U](map: java.util.Map[T, U]): Map[T, U] =
    map.asScala.toMap

}
