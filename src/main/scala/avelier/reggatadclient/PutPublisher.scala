package avelier.reggatadclient

import java.util.ArrayList

import org.reactivestreams.{Publisher, Subscriber}

import scala.collection.mutable

/**
  * Created by av-elier on 20.02.17.
  */
class PutPublisher[T] extends Publisher[T] {
  private val ss = mutable.MutableList[Subscriber[_ >: T]]()
  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    ss += s
  }

  def put(t: T): Unit = ss.foreach(s => s.onNext(t))
  def error(e: Throwable): Unit = ss.foreach(s => s.onError(e))
  def complete(): Unit = {
    ss.foreach(s => s.onComplete())
    ss.clear()
  }
}
