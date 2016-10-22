package avelier.reggatadclient

import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.effect.DropShadow
import scalafx.scene.layout.HBox
import scalafx.scene.paint.{LinearGradient, Stops}
import scalafx.scene.text.Text
import scalafx.scene.paint.Color._

/**
  * Created by av-elier on 21.10.16.
  */
object Main extends JFXApp {
  val rgt = new Reggata()
  //  rgt.msgReqQueue.add(Reggata.CloseRepo("/home/av-elier/tmp", "/home/av-elier/tmp/.reggata/", true))
  //  rgt.msgReqQueue.add(Reggata.CloseRepo("/home/av-elier/tmp")) // regattad not yet supports
  rgt.msgReqQueue.add(Reggata.AddTags("/home/av-elier/tmp/test.txt", Array("tag_scala")))


  stage = new PrimaryStage {
    title = "ScalaFX Hello World"
    scene = new Scene {
      fill = Black
      content = new HBox {
        padding = Insets(20)
        children = Seq(
          new Text {
            text = "Hello "
            style = "-fx-font-size: 48pt"
            fill = new LinearGradient(
              endX = 0,
              stops = Stops(PaleGreen, SeaGreen))
          },
          new Text {
            text = "World!!!"
            style = "-fx-font-size: 48pt"
            fill = new LinearGradient(
              endX = 0,
              stops = Stops(Cyan, DodgerBlue)
            )
            effect = new DropShadow {
              color = DodgerBlue
              radius = 25
              spread = 0.25
            }
          }
        )
      }
    }
  }
}
