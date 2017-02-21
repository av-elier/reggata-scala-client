package avelier.reggatadclient

import java.nio.{ByteBuffer, ByteOrder}

import avelier.reggatadclient.ReggataMessages.RgtReqMsgBox.Cmd
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, Reads, Writes, _}

import scala.util.Random

/**
  * Created by av-elier on 28.01.17.
  */
object ReggataMessages {

  class ReggattaProtocolException(msg: String = "") extends Exception(msg: String)

  sealed trait MsgFromRgt
  object MsgFromRgt {
    def apply[T >: MsgFromRgt](str: String)(implicit reader: Reads[T]): Option[T] = {
      (
        Json.parse(str).validate[RespMsgFromRgt] match {
          case m: JsSuccess[RespMsgFromRgt] =>
            Some(m.get)
          case _ =>
            None
        }).orElse {
        Json.parse(str).validate[PingMsgFromRgt] match {
          case ping: JsSuccess[PingMsgFromRgt] =>
            Some(ping.get)
          case e: JsError =>
            None
        }
      }
    }
  }

  case class RespMsgFromRgt(id: String, code: Int, msg: Option[String]) extends MsgFromRgt

  case class PingMsgFromRgt(question: String) extends MsgFromRgt


  sealed trait MsgToRgt {
    def getRgtBytes[T >: MsgToRgt](implicit writer: Writes[T]): Array[Byte] = {
      val msgData = Json.toJson(this).toString().getBytes
      ByteBuffer
        .allocate(4 + msgData.length)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(msgData.length)
        .put(msgData)
        .array()
    }
  }

  case class PongMsgToRgt(answer: String) extends MsgToRgt

  case class RgtReqMsgBox(id: String, cmd: String, rgtReqMsg: Cmd) extends MsgToRgt

  object RgtReqMsgBox {
    sealed trait Cmd
    def apply(rgtReqMsg: Cmd, id: String = Random.nextLong.toString): RgtReqMsgBox = {
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

    case class OpenRepo(rootDir: String, dbDir: Option[String] = None, init: Boolean = true) extends Cmd

    case class CloseRepo(rootDir: String) extends Cmd

    case class AddTags(file: String, tags: Array[String]) extends Cmd

    case class RemoveTags(file: String, tags: Array[String]) extends Cmd

    case class AddFields(stub: String) extends Cmd

    case class RemoveFields(stub: String) extends Cmd

    case class GetFileInfo(file: String) extends Cmd

    case class Search(path: String, query: String) extends Cmd

    case class CancelCmd(id: String) extends Cmd
  }

  import RgtReqMsgBox._


  // Implicit conversion to/from json

  implicit val pongWrites: Writes[PongMsgToRgt] = (__ \ "answer").write[String].contramap(unlift(PongMsgToRgt.unapply))

  implicit val openRepoWrites: Writes[OpenRepo] = (
    (__ \ "root_dir").write[String] and
      (__ \ "db_dir").write[String] and
      (__ \ "init_if_not_exists").write[Boolean]
    ) (c => (c.rootDir, c.dbDir.getOrElse(s"${c.rootDir}/.rgt"), c.init))

  implicit val closeRepoWrites: Writes[CloseRepo] = (__ \ "root_dir").write[String].contramap(unlift(CloseRepo.unapply))

  implicit val addTagsWrites: Writes[AddTags] = (
    (__ \ "file").write[String] and
      (__ \ "tags").write[Array[String]]
    ) (unlift(AddTags.unapply))

  implicit val removeTagsWrites: Writes[RemoveTags] = (
    (__ \ "file").write[String] and
      (__ \ "tags").write[Array[String]]
    ) (unlift(RemoveTags.unapply))

  implicit val addFieldsWrites: Writes[AddFields] = Json.writes[AddFields] // TODO

  implicit val removeFieldsWrites: Writes[RemoveFields] = Json.writes[RemoveFields] // TODO

  implicit val getFileInfoWrites: Writes[GetFileInfo] = (__ \ "file").write[String].contramap(unlift(GetFileInfo.unapply))

  implicit val searchWrites: Writes[Search] = (
    (__ \ "path").write[String] and
      (__ \ "query").write[String]
    ) (unlift(Search.unapply))

  implicit val cancelCmdWrites: Writes[CancelCmd] = (__ \ "cmd_id").write[String].contramap(unlift(CancelCmd.unapply))

  implicit val regPingWrites: Writes[PongMsgToRgt] = (__ \ "ping").write[String].contramap(unlift(PongMsgToRgt.unapply))


  implicit val rgtCmdWrites: Writes[Cmd] = Writes[Cmd] {
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
      (__ \ "args").write[Cmd]
    ) (unlift(RgtReqMsgBox.unapply))

  implicit val msgToRgtWrites: Writes[MsgToRgt] = Writes[MsgToRgt] {
    case m: PongMsgToRgt => pongWrites.writes(m)
    case m: RgtReqMsgBox => rgtReqMsgBoxWrites.writes(m)
  }

  implicit val rgtPingMsgFromRgt: Reads[PingMsgFromRgt] = (__ \ "question").read[String].map(cmd => PingMsgFromRgt(cmd))

  implicit val rgtRespMsgBoxReads: Reads[RespMsgFromRgt] = (
    (__ \ "id").read[String] and
      (__ \ "code").read[Int] and
      (__ \ "msg").readNullable[String]
    ) (RespMsgFromRgt.apply _)

  implicit val msgFromRgtReads: Reads[MsgFromRgt] = Reads[MsgFromRgt] {
    case m: PingMsgFromRgt => rgtPingMsgFromRgt.reads(m)
    case m: RespMsgFromRgt => rgtRespMsgBoxReads.reads(m)
  }
}
