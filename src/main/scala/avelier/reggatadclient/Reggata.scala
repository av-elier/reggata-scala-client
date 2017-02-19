package avelier.reggatadclient

import java.io.{InputStream, OutputStream}
import java.nio.{ByteBuffer, ByteOrder}
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.logging.Logger
import java.util.logging.Level._

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import avelier.reggatadclient.ReggataMessages._
import org.reactivestreams.Publisher

import scala.util.Try
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by av-elier on 21.10.16.
  */
object Reggata {

  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()

  val host: String = Settings.Reggatad.host
  val port: Int = Settings.Reggatad.port
  private val msgToRgtQueue = new ArrayBlockingQueue[MsgToRgt](Settings.Reggatad.reqBlockingQueueSize, true)
  private val msgFromRgtQueue = new ArrayBlockingQueue[MsgFromRgt](Settings.Reggatad.respBlockingQueueSize, true)

  val msgFromRgtSource: Source[MsgFromRgt, NotUsed] = Source.fromIterator(() => Iterator.continually(msgFromRgtQueue.take()))
  val msgToRgtSource: Sink[MsgToRgt, Future[akka.Done]] = Sink.foreach[MsgToRgt](msg => msgToRgtQueue.put(msg))

  def conn = Try(new Socket(host, port))
  val log = Logger.getLogger("reggata")

  private def writeLoop(out: OutputStream) = {
    log.info("writeLoop started")
    while (true) {
      val msg = msgToRgtQueue.take()
      msg match {
        case boxedMsg: RgtReqMsgBox =>
          log.info(s"writeLoop writing msg $boxedMsg")
        case msg: PongMsgToRgt =>
          log.info(s"writeLoop writing $msg")
      }
      out.write(msg.getRgtBytes)
      out.flush()
    }
  }

  private def readLoop(in: InputStream) = {
    log.info("readLoop started")
    while (true) {
      val size = {
        val buf = new Array[Byte](4)
        val l = in.read(buf)
        if (l != 4) throw new ReggattaProtocolException("expected 4 bytes size")
        val size = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getInt
        log.info(s"Size header is $size")
        size
      }
      val msgJson = {
        val buf = new Array[Byte](size)
        val l = in.read(buf)
        if (l != size) throw new ReggattaProtocolException(s"expected $size bytes of msg")
        new String(buf)
      }
      log.fine(s"readLoop read msg $msgJson")

      val msg = MsgFromRgt(msgJson) // rewrite in akka streams
      msg match {
        case Some(x) =>
          log.info (s"readLoop reads msg $msg")
          x match {
            case _: PingMsgFromRgt =>
              msgToRgtQueue.add(PongMsgToRgt("Yes")) // msgToRgtSource
            case x: RespMsgFromRgt =>
              ???
          }
        case None =>
          log.warning(s"could not parse MsgFromRgt $msgJson")
//          throw new ReggattaProtocolException(s"could not parse MsgFromRgt $msgJson") // TODO: everything should be parsed
      }
    }
  }

  val socketThread = new Thread(new Runnable {
    override def run(): Unit = {
      log.info("socketThread running")
      conn.map(sock => {
        val (in, out) = (sock.getInputStream, sock.getOutputStream)
          val writeFuture = Future(writeLoop(out))
          val readFuture = Future(readLoop(in))

          val failed = Future.firstCompletedOf(Seq(readFuture, writeFuture))
          failed.recover { case e: Throwable =>
            log.warning(s"socketThread - future error $e")
            throw e
          }
        }.recover { case e =>
          Thread.sleep(Settings.Reggatad.retryTimeout)
          log.log(WARNING, "reggata socket error", e)
        })
    }
  })

  socketThread.start()




}
