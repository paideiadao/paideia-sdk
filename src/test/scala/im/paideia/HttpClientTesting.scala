package im.paideia

import scalan.util.FileUtil
import org.ergoplatform.appkit.JavaHelpers._
import java.util.{List => JList}
import java.lang.{String => JString}

trait HttpClientTesting {
  val responsesDir = "src/test/resources"
  val addr1 = "9f4QF8AD1nQ3nJahQVkMj8hFSVVzVom77b52JU7EW71Zexg6N8v"

  def loadNodeResponse(name: String) = {
    FileUtil.read(FileUtil.file(s"$responsesDir/node_responses/$name"))
  }

  def loadExplorerResponse(name: String) = {
    FileUtil.read(FileUtil.file(s"$responsesDir/explorer_responses/$name"))
  }

  case class MockData(nodeResponses: Seq[String] = Nil, explorerResponses: Seq[String] = Nil) {
    def appendNodeResponses(moreResponses: Seq[String]): MockData = {
      this.copy(nodeResponses = this.nodeResponses ++ moreResponses)
    }
    def appendExplorerResponses(moreResponses: Seq[String]): MockData = {
      this.copy(explorerResponses = this.explorerResponses ++ moreResponses)
    }
  }

  object MockData {
    def empty = MockData()
  }

  def createMockedErgoClient(data: MockData, nodeOnlyMode: Boolean = false): FileMockedErgoClient = {
    val nodeResponses = IndexedSeq(
      loadNodeResponse("response_NodeInfo.json"),
      loadNodeResponse("response_LastHeaders.json")) ++ data.nodeResponses
    val explorerResponses: IndexedSeq[String] = data.explorerResponses.toIndexedSeq
    new FileMockedErgoClient(
      nodeResponses.convertTo[JList[JString]],
      explorerResponses.convertTo[JList[JString]],
      nodeOnlyMode)
  }
}