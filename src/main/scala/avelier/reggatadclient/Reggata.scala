package avelier.reggatadclient

import java.io.{InputStream, OutputStream}
import java.nio.{ByteBuffer, ByteOrder}
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.logging.Logger
import java.util.logging.Level._

import avelier.reggatadclient.ReggataMessages._
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.util.{Random, Try}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by av-elier on 21.10.16.
  */
object Reggata {

  val host: String = Settings.Reggatad.host
  val port: Int = Settings.Reggatad.port
  val msgReqQueue = new ArrayBlockingQueue[RgtReqMsg](Settings.Reggatad.reqBlockingQueueSize, true)
  val msgRespQueue = new ArrayBlockingQueue[RgtRespMsg](Settings.Reggatad.respBlockingQueueSize, true)


  def conn = Try(new Socket(host, port))
  val log = Logger.getLogger("reggata")

  private def writeLoop(out: OutputStream) = {
    log.info("writeLoop started")
    while (true) {
      val msg = msgReqQueue.take()
      val boxedMsg = RgtReqMsgBox(msg)
      log.fine(s"writeLoop writing msg $boxedMsg")

      out.write(boxedMsg.getRgtBytes)
      out.flush()
    }
  }

  private def readLoop(in: InputStream) = {
    log.info("readLoop started")
    while (true) {
      val buf = new Array[Byte](1024)
      val l = in.read(buf)
      val size = {
        val buf = new Array[Byte](4)
        val l = in.read(buf)
        if (l != 4) throw new RegattadProtocolException("expected 4 bytes size")
        val size = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getInt
        log.info(s"Size header is $size")
        size
      }
      val msgJson = {
        val buf = new Array[Byte](size)
        val l = in.read(buf)
        if (l != size) throw new RegattadProtocolException(s"expected $size bytes of msg")
        new String(buf)
      }
      log.fine(s"readLoop read msg $msgJson")

      val msg = Json.parse(msgJson).validate[RgtRespMsgBox] match {
        case s: JsSuccess[RgtRespMsgBox] => {
          val msgBox = s.get
          log.info(s"readLoop reads msg $msgBox")
          msgBox.rgtRespMsg.foreach(msgRespQueue.put)
        }
        case e: JsError => throw new RegattadProtocolException(s"error parsing $msgJson: $e")
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
