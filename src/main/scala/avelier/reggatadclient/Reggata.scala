package avelier.reggatadclient

import java.io.{InputStream, OutputStream}
import java.nio.{ByteBuffer, ByteOrder}
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.logging.Logger
import java.util.logging.Level._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.util.{Random, Try}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import Reggata._

/**
  * Created by av-elier on 21.10.16.
  */
class Reggata {

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
      conn.map(sock => (sock.getInputStream, sock.getOutputStream))
        .map { case (in, out) =>
          val writeFuture = Future(writeLoop(out))
          val readFuture = Future(readLoop(in))

          val failed = Future.firstCompletedOf(Seq(readFuture, writeFuture))
          failed.recover { case e: Throwable =>
            log.warning(s"socketThread - future error $e")
            throw e
          }
        }
        .recover { case e =>
          Thread.sleep(Settings.Reggatad.retryTimeout)
          log.log(WARNING, "reggata socket error", e)
        }
    }
  })

  socketThread.start()

}

object Reggata {

  class RegattadProtocolException(msg: String = "") extends Exception(msg: String)

  sealed trait RgtRespMsg
  case class RgtRespMsgBox(
                          cmd: String,
                          rgtRespMsg: Option[RgtRespMsg]
                          )
  case class Ping() extends RgtRespMsg

  sealed trait RgtReqMsg
  case class RgtReqMsgBox(
                    id: String,
                    cmd: String,
                    rgtReqMsg: RgtReqMsg
                    ) {
    def getRgtBytes[RgtReqMsgBox](implicit writer: Writes[RgtReqMsgBox]): Array[Byte] = {
      val msgData = Json.toJson(this).toString().getBytes
      ByteBuffer
        .allocate(4 + msgData.length)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(msgData.length)
        .put(msgData)
        .array()
    }
  }
  object RgtReqMsgBox {
    def apply(rgtReqMsg: RgtReqMsg, id: String = Random.nextLong.toString): RgtReqMsgBox = {
      val cmd = rgtReqMsg match {
        case _: OpenRepo => "open_repo"
        case _: CloseRepo => "close_repo"
        case _: AddTags => "add_tags"
        case _: RemoveTags => "remove_tags"
        case _: AddFields => "add_fields"
        case _: RemoveFields => "remove_fields"
        case _: GetFileInfo => "get_file_info"
        case _: Search => "search"
        case _: CancelCmd => "cancel_cmd"
      }
      RgtReqMsgBox(id, cmd, rgtReqMsg)
    }
  }
  case class OpenRepo(rootDir: String, dbDir: String, init: Boolean) extends RgtReqMsg
  case class CloseRepo(rootDir: String) extends RgtReqMsg
  case class AddTags(file: String, tags: Array[String]) extends RgtReqMsg
  case class RemoveTags(file: String, tags: Array[String]) extends RgtReqMsg
  case class AddFields(stub: String) extends RgtReqMsg
  case class RemoveFields(stub: String) extends RgtReqMsg
  case class GetFileInfo(file: String) extends RgtReqMsg
  case class Search(path: String, query: String) extends RgtReqMsg
  case class CancelCmd(id: String) extends RgtReqMsg

  // Implicit conversion to/from json

  implicit val openRepoWrites: Writes[OpenRepo] = (
    (__ \ "root_dir").write[String] and
      (__ \ "db_dir").write[String] and
      (__ \ "root_dir").write[Boolean]
    )(unlift(OpenRepo.unapply))

  implicit val closeRepoWrites: Writes[CloseRepo] = (__ \ "root_dir").write[String].contramap(unlift(CloseRepo.unapply))

  implicit val addTagsWrites: Writes[AddTags] = (
    (__ \ "file").write[String] and
      (__ \ "tags").write[Array[String]]
    )(unlift(AddTags.unapply))

  implicit val removeTagsWrites: Writes[RemoveTags] = (
    (__ \ "file").write[String] and
      (__ \ "tags").write[Array[String]]
    )(unlift(RemoveTags.unapply))

  implicit val addFieldsWrites: Writes[AddFields] = Json.writes[AddFields] // TODO

  implicit val removeFieldsWrites: Writes[RemoveFields] = Json.writes[RemoveFields] // TODO

  implicit val getFileInfoWrites: Writes[GetFileInfo] = (__ \ "file").write[String].contramap(unlift(GetFileInfo.unapply))

  implicit val searchWrites: Writes[Search] = (
    (__ \ "path").write[String] and
      (__ \ "query").write[String]
    )(unlift(Search.unapply))

  implicit val cancelCmdWrites: Writes[CancelCmd] = (__ \ "cmd_id").write[String].contramap(unlift(CancelCmd.unapply))

  implicit val rgtReqMsgWrites: Writes[RgtReqMsg] = Writes[RgtReqMsg]{
    case m: OpenRepo => openRepoWrites.writes(m)
    case m: CloseRepo => closeRepoWrites.writes(m)
    case m: AddTags => addTagsWrites.writes(m)
    case m: RemoveTags => removeTagsWrites.writes(m)
    case m: AddFields => addFieldsWrites.writes(m)
    case m: RemoveFields => removeFieldsWrites.writes(m)
    case m: GetFileInfo => getFileInfoWrites.writes(m)
    case m: Search => searchWrites.writes(m)
    case m: CancelCmd => cancelCmdWrites.writes(m)
  }

  implicit val rgtReqMsgBoxWrites = (
    (__ \ "id").write[String] and
      (__ \ "cmd").write[String] and
      (__ \ "args").write[RgtReqMsg]
    )(unlift(RgtReqMsgBox.unapply))

  implicit val rgtRespMsgBoxReads: Reads[RgtRespMsgBox] = (__ \ "cmd").read[String].map(cmd => RgtRespMsgBox(cmd, None))

}
