package avelier.reggatadclient

import java.nio.{ByteBuffer, ByteOrder}

import avelier.reggatadclient.Reggata._
import avelier.reggatadclient.ReggataMessages.{MsgFromRgt, RespMsgFromRgt, _}
import avelier.reggatadclient.ReggataMessages.RgtReqMsgBox._
import org.scalatest._

/**
  * Created by av-elier on 22.10.16.
  */
class ReggataSpec extends FlatSpec {

  "Reggata commands" should "serialize to reggatad format" in {

    val bytes = RgtReqMsgBox(OpenRepo("~", Some("~/.reggata"), false), "123").getRgtBytes
    val size = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt
    val json = new String(bytes, 4, size)

    assert(size == 103)
    assert(json == """{"id":"123","cmd":"open_repo","args":{"root_dir":"~","db_dir":"~/.reggata","init_if_not_exists":false}}""")
  }

  "Reggatad responses" should "be parsed" in {
    val msg: Option[MsgFromRgt] = MsgFromRgt("""{"code":400,"id":"1032357844592570421","msg":"Could not find repo for path \"/home/foo/bar\""}""")
    msg match {
      case None => assert(false, "msg should be parsed, but it's not")
      case Some(m) => assert(m == RespMsgFromRgt("1032357844592570421", 400, Some("Could not find repo for path \"/home/foo/bar\"")))
    }
  }
}
