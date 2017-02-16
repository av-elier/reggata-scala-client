package avelier.reggatadclient

import java.io.{File, IOException}
import java.net.URL
import java.util.{PropertyResourceBundle, ResourceBundle}
import java.util.concurrent.ScheduledThreadPoolExecutor
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.{MenuItem, TreeTableColumn, TreeTableView}

import avelier.reggatadclient.ReggataMessages.RgtReqMsgBox.{OpenRepo, GetFileInfo}
import avelier.reggatadclient.ReggataMessages._

import scala.concurrent.{ExecutionContext, Future}
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.ReadOnlyStringWrapper
import scalafx.event.ActionEvent
import scalafx.scene.control.{TreeItem, TreeTableRow}
import scalafx.scene.input.{MouseButton, MouseEvent}
import scalafx.stage.DirectoryChooser

/**
  * Created by av-elier on 23.10.16.
  */
class MainWindowController extends Initializable {
  @FXML var filesTree: TreeTableView[File] = _
  @FXML var dirsColName: TreeTableColumn[File, String] = _
  @FXML var menuNew: MenuItem = _

  val bundle = new PropertyResourceBundle(getClass.getResource("/view/main-window-en.properties").openStream)

  implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(new ScheduledThreadPoolExecutor(1))

  override def initialize(url: URL, rb: ResourceBundle) {
    val currentDir = new File("/home/av-elier/Pictures")
    val selectedDir = new File("/home/av-elier/Pictures/.reggata") // TODO: open last or open user-selected
    filesTree.rowFactory = x => {
      new TreeTableRow[File] {
        onMouseClicked = (event: MouseEvent) => if (event.button == MouseButton.Primary) onFileSelect(item.value)
      }
    }
    dirsColName.cellValueFactory = x => new ReadOnlyStringWrapper(dirsColName, "file", x.value.getValue.getName)
    Future(findFiles(currentDir, selectedDir))

    menuNew.onAction = (event: ActionEvent) => {
      val dialog = new DirectoryChooser()
      dialog.setTitle(bundle.getString("dialog.openrepo.title"))

      val result = Option(dialog.showDialog(Main.stage))
      result.foreach(file => {
        Reggata.msgToRgtQueue.add(RgtReqMsgBox(OpenRepo(file.getAbsolutePath)))
      })
    }
  }

  private def findFiles(dir: File, expandTo: File, parent: TreeItem[File] = null) {
    val root = new TreeItem[File](dir)
    Future(Platform.runLater(root.setExpanded(expandTo.getAbsolutePath startsWith dir.getAbsolutePath)))
    try {
      val files = dir.listFiles()
      for (file <- files) {
        if (file.isDirectory) {
          findFiles(file, expandTo, root)
        } else {
          Future(Platform.runLater(root.getChildren.add(new TreeItem(file))))
        }
      }
      if (parent == null) {
        Future(Platform.runLater(filesTree.setRoot(root)))
      } else {
        Future(Platform.runLater(parent.getChildren.add(root)))
      }
    } catch {
      case e: IOException => e.printStackTrace()
    }
  }

  private def onFileSelect(f: File) = {
    Reggata.msgToRgtQueue.put(RgtReqMsgBox(GetFileInfo( f.getAbsolutePath )))
    // TODO: handle response
  }
}
