package avelier.reggatadclient

import java.io.{File, IOException}
import java.net.URL
import java.util.ResourceBundle
import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor}
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.{TreeTableColumn, TreeTableView}

import scala.concurrent.{ExecutionContext, Future}
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.ReadOnlyStringWrapper
import scalafx.scene.control.TreeItem

/**
  * Created by av-elier on 23.10.16.
  */
class MainWindowController extends Initializable {
  @FXML var filesTree: TreeTableView[File] = _
  @FXML var dirsColName: TreeTableColumn[File, String] = _

  implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(new ScheduledThreadPoolExecutor(1))

  override def initialize(url: URL, rb: ResourceBundle) {
    val currentDir = new File("/home/av-elier/tmp")
    val selectedDir = new File("/home/av-elier/tmp/.reggata")
    dirsColName.cellValueFactory = x => new ReadOnlyStringWrapper(dirsColName, "file", x.value.getValue.getName)
    Future(findFiles(currentDir, selectedDir))
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
}
