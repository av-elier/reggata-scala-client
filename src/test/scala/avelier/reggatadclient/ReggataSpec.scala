package avelier.reggatadclient

import java.nio.{ByteBuffer, ByteOrder}

import avelier.reggatadclient.Reggata._
import org.scalatest._

/**
  * Created by av-elier on 22.10.16.
  */
class ReggataSpec extends FlatSpec {

  "Reggata commands" should "serialize to reggatad format" in {
    val bytes = RgtReqMsgBox(OpenRepo("~", "~/.reggata", false), "123").getRgtBytes(rgtReqMsgBoxWrites)
    val size = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt
    val json = new String(bytes, 4, size)

    assert(size == 78)
    assert(json == """{"id":"123","cmd":"open_repo","args":{"root_dir":false,"db_dir":"~/.reggata"}}""")
  }
}
