package avelier.reggatadclient

import java.util.PropertyResourceBundle
import javafx.fxml.FXMLLoader

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafx.stage.WindowEvent

/**
  * Created by av-elier on 21.10.16.
  */
object Main extends JFXApp {

  val bundle = new PropertyResourceBundle(getClass.getResource("/view/main-window-en.properties").openStream)
  val fxml = getClass.getResource("/view/main-window.fxml")
  val root: javafx.scene.Parent = FXMLLoader.load(fxml, bundle)
  root.getStylesheets.clear()
  root.getStylesheets.add("/view/main.css")

  stage = new PrimaryStage() {
    title = "Reggata Scala"
    scene = new Scene(root)
    onCloseRequest = (event: WindowEvent) => System.exit(0)
  }
}
