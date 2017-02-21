package avelier.reggatadclient

import java.io.{File, IOException}
import java.net.URL
import java.util.{PropertyResourceBundle, ResourceBundle}
import java.util.concurrent.ScheduledThreadPoolExecutor
import javafx.fxml.{FXML, Initializable}
import javafx.scene.control.{MenuItem, TreeTableColumn, TreeTableView}

import akka.stream.scaladsl.{Flow, Sink, Source}
import avelier.reggatadclient.ReggataMessages.RgtReqMsgBox.{GetFileInfo, OpenRepo}
import avelier.reggatadclient.ReggataMessages._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try
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
    val defaultDir = "/home/av-elier/Pictures" // TODO: open last or open user-selected
    val currentDir = new File(defaultDir )
    val selectedDir = new File(s"$defaultDir/.reggata")
    Reggata.toRgt(RgtReqMsgBox(OpenRepo(defaultDir)))

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
        Reggata.toRgt(RgtReqMsgBox(OpenRepo(file.getAbsolutePath)))
        val p = Promise[Unit]()
        val ks = Reggata.addRgtSink(Sink.foreach[MsgFromRgt](x => {
          println(s"Got responce in MainWindowController $x")
          p.complete(Try(()))
        }))
        p.future.foreach(_ => ks.shutdown())
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
    Reggata.toRgt(RgtReqMsgBox(GetFileInfo( f.getAbsolutePath )))
    // TODO: handle response
  }
}
