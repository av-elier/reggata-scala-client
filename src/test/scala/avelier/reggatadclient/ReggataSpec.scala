package avelier.reggatadclient

import java.nio.{ByteBuffer, ByteOrder}

import avelier.reggatadclient.ReggataMessages.RespMsgFromRgt.RgtRespAny
import avelier.reggatadclient.ReggataMessages._
import avelier.reggatadclient.ReggataMessages.RgtReqMsgBox._
import org.scalatest._
import play.api.libs.json._

/**
  * Created by av-elier on 22.10.16.
  */
class ReggataSpec extends FunSpec {

  describe("Reggata commands") {
    it("serialize to reggatad format") {
      val bytes = RgtReqMsgBox(OpenRepo("~", Some("~/.reggata"), false), "123").getRgtBytes
      val size = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt
      val json = new String(bytes, 4, size)

      assert(size == 103)
      assert(json == """{"id":"123","cmd":"open_repo","args":{"root_dir":"~","db_dir":"~/.reggata","init_if_not_exists":false}}""")
    }
  }


  val parseParis = Seq(
    """{"code":400,"id":"1032357844592570421","msg":"Could not find repo for path \"/home/foo/bar\""}"""
      ->
      RespMsgFromRgt("1032357844592570421", 400, Some("Could not find repo for path \"/home/foo/bar\""), None),

    """{"code":200,"data":{"path":"1.txt","size":6,"tags":[]},"id":"15154743533434955"}"""
      ->
      RespMsgFromRgt("15154743533434955", 200, None, Some(RgtRespAny(JsObject(Seq(
        "path" -> JsString("1.txt"),
        "size" -> JsNumber(6),
        "tags" -> JsArray()
      )))))
  )

  describe("Reggatad responses") {
    for ((json, expectedMsg) <- parseParis) {
      it(s"be parsed is various jsons ($json)") {
        val msg: Option[MsgFromRgt] = MsgFromRgt(json)
        msg match {
          case None => assert(false, "msg should be parsed, but it's not")
          case Some(m) => assert(m == expectedMsg)
        }
      }
    }
  }
}
