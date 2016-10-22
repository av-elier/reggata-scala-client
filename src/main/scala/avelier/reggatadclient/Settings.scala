package avelier.reggatadclient

import com.typesafe.config.ConfigFactory

/**
  * Created by av-elier on 22.10.16.
  */
object Settings {
  val config = ConfigFactory.load()

  object Reggatad {
    val host = config.getString("reggatad.host")
    val port = config.getInt("reggatad.port")
    val reqBlockingQueueSize = 1000
    val respBlockingQueueSize = 1000
    val retryTimeout = 5000
  }
}