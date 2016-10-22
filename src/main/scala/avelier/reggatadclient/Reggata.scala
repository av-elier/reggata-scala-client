package avelier.reggatadclient

import java.io.{InputStream, OutputStream}
import java.net.Socket
import java.nio.{ByteBuffer, ByteOrder}
import java.util.concurrent.ArrayBlockingQueue
import java.util.logging.Logger
import java.util.logging.Level._

import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.util.{Random, Try}
import Reggata._

/**
  * Created by av-elier on 21.10.16.
  */
class Reggata(
             val host: String = "127.0.0.1",
             val port: Int = 9100
             ) {

  val msgReqQueue = new ArrayBlockingQueue[RgtReqMsg](1000, true)


  def conn = Try(new Socket(host, port))
  val log = Logger.getLogger("reggata")

  private def writeLoop(in: InputStream, out: OutputStream) = {
    log.info("writeLoop started")
    while (true) {
      val msg = msgReqQueue.take()
      log.fine(s"writeLoop taken msg $msg")
      val boxedMsg = RgtReqMsgBox(msg)
      log.fine("writeLoop writing msg")

      out.write(boxedMsg.getRgtBytes)
      out.flush()
    }
  }

  val writeThread = new Thread(new Runnable {
    override def run(): Unit = {
      log.info("thread running")
      while (true) {
        conn.map(sock => (sock.getInputStream, sock.getOutputStream))
          .map{ case (in, out) => writeLoop(in, out) }
          .recover { case e => log.log(WARNING, "reggata socket error", e) }
      }
    }
  })

}

object Reggata {

  class RegattadProtocolException(msg: String = "") extends Exception(msg: String)

  sealed trait RgtRespMsg

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

  implicit val openRepoWrites: Writes[OpenRepo] = (
    (__ \ "root_dir").write[String] and
      (__ \ "db_dir").write[String] and
      (__ \ "root_dir").write[Boolean]
    )(unlift(OpenRepo.unapply))

  implicit val closeRepoWrites: Writes[CloseRepo] = (__ \ "root_dir").write[String].contramap((unlift(CloseRepo.unapply)))

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

  implicit val cancelCmdWrites: Writes[CancelCmd] = (__ \ "cmd_id").write[String].contramap((unlift(CancelCmd.unapply)))

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

}
