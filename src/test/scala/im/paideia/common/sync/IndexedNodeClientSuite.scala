package im.paideia.common.sync

import org.scalatest.funsuite.AnyFunSuite
import org.ergoplatform.explorer.client.model.Items
import org.ergoplatform.explorer.client.model.TotalBalance
import org.ergoplatform.restapi.client.BlockchainApi
import org.ergoplatform.restapi.client.BlockchainIndexHeight
import org.ergoplatform.restapi.client.BlockchainToken
import org.ergoplatform.restapi.client.BlockchainTransaction
import org.ergoplatform.restapi.client.ErgoTransactionOutput

import scala.collection.JavaConverters._
import scala.collection.mutable

/** Covers [[IndexedNodeClient]] against a hand-rolled `BlockchainApi` stub (same style as
  * `StubCall` - no real HTTP round-trip): `indexHeight`'s field choice,
  * `unspentBoxesByErgoTree`'s pagination (full pages concatenated in order, stopping at a
  * short page), and that it retries a failed page through the same [[NodeCalls.retry]]
  * path [[NodeBlockSourceSuite]] already covers for [[NodeBlockSource]].
  */
class IndexedNodeClientSuite extends AnyFunSuite {

  private def success[T](body: T): retrofit2.Response[T] = retrofit2.Response.success(body)

  private def dummyBox(id: String): ErgoTransactionOutput =
    new ErgoTransactionOutput().boxId(id)

  /** Serves `getBlockchainIndexHeight` from a single `StubCall` and `getUnspentBoxesByErgoTree`
    * from one `StubCall` per offset - every other `BlockchainApi` endpoint is unused by
    * [[IndexedNodeClient]] and left unimplemented.
    */
  private class StubBlockchainApi(
    indexHeightCall: StubCall[BlockchainIndexHeight],
    pagesByOffset: Map[Int, StubCall[java.util.List[ErgoTransactionOutput]]]
  ) extends BlockchainApi {
    val requestedOffsets: mutable.Buffer[Int] = mutable.Buffer.empty

    override def getBlockchainIndexHeight(): retrofit2.Call[BlockchainIndexHeight] =
      indexHeightCall

    override def getUnspentBoxesByErgoTree(
      ergoTree: String,
      limit: Integer,
      offset: Integer,
      sortDirection: String
    ): retrofit2.Call[java.util.List[ErgoTransactionOutput]] = {
      requestedOffsets += offset.intValue()
      pagesByOffset(offset.intValue())
    }

    override def getBoxById(boxId: String): retrofit2.Call[ErgoTransactionOutput] =
      throw new UnsupportedOperationException("not used by IndexedNodeClient")
    override def getUnspentBoxesByAddress(
      address: String,
      limit: Integer,
      offset: Integer,
      sortDirection: String
    ): retrofit2.Call[java.util.List[ErgoTransactionOutput]] =
      throw new UnsupportedOperationException("not used by IndexedNodeClient")
    override def getTxById(txId: String): retrofit2.Call[BlockchainTransaction] =
      throw new UnsupportedOperationException("not used by IndexedNodeClient")
    override def getTxByIndex(txIdx: String): retrofit2.Call[BlockchainTransaction] =
      throw new UnsupportedOperationException("not used by IndexedNodeClient")
    override def getTransactionsByAddress(
      address: String,
      offset: Integer,
      limit: Integer
    ): retrofit2.Call[Items[BlockchainTransaction]] =
      throw new UnsupportedOperationException("not used by IndexedNodeClient")
    override def getTokenById(tokenId: String): retrofit2.Call[BlockchainToken] =
      throw new UnsupportedOperationException("not used by IndexedNodeClient")
    override def getBalance(address: String): retrofit2.Call[TotalBalance] =
      throw new UnsupportedOperationException("not used by IndexedNodeClient")
  }

  test("indexHeight returns the index height, not the node's full chain height") {
    val bih = new BlockchainIndexHeight()
    bih.setFullHeight(999L)
    bih.setIndexedHeight(123L)
    val api = new StubBlockchainApi(
      indexHeightCall = new StubCall(Seq(() => success(bih))),
      pagesByOffset   = Map.empty
    )

    assert(new IndexedNodeClient(api).indexHeight() == 123)
  }

  test("unspentBoxesByErgoTree pages through full pages and stops at a short page, concatenated in order") {
    val page0 = (0 until 100).map(i => dummyBox(f"p0-$i%03d")).toList.asJava
    val page1 = (0 until 100).map(i => dummyBox(f"p1-$i%03d")).toList.asJava
    val page2 = (0 until 7).map(i => dummyBox(f"p2-$i%03d")).toList.asJava
    val api = new StubBlockchainApi(
      indexHeightCall = new StubCall(Seq(() => success(new BlockchainIndexHeight()))),
      pagesByOffset = Map(
        0   -> new StubCall(Seq(() => success(page0))),
        100 -> new StubCall(Seq(() => success(page1))),
        200 -> new StubCall(Seq(() => success(page2)))
      )
    )

    val result = new IndexedNodeClient(api).unspentBoxesByErgoTree("deadbeef")

    assert(result.map(_.getBoxId()) == (page0.asScala ++ page1.asScala ++ page2.asScala).map(_.getBoxId()))
    assert(api.requestedOffsets == Seq(0, 100, 200))
  }

  test("unspentBoxesByErgoTree stops immediately when the first page is already short (including empty)") {
    val api = new StubBlockchainApi(
      indexHeightCall = new StubCall(Seq(() => success(new BlockchainIndexHeight()))),
      pagesByOffset   = Map(0 -> new StubCall(Seq(() => success(java.util.Collections.emptyList())))
      )
    )

    assert(new IndexedNodeClient(api).unspentBoxesByErgoTree("deadbeef") == Nil)
    assert(api.requestedOffsets == Seq(0))
  }

  test("unspentBoxesByErgoTree retries a failed page fetch once via the shared NodeCalls retry path") {
    val retries = mutable.Buffer[(String, Int, String)]()
    val page0   = List(dummyBox("only")).asJava
    val flaky = new StubCall[java.util.List[ErgoTransactionOutput]](
      Seq(
        () => throw new java.io.IOException("connection reset"),
        () => success(page0)
      )
    )
    val api = new StubBlockchainApi(
      indexHeightCall = new StubCall(Seq(() => success(new BlockchainIndexHeight()))),
      pagesByOffset   = Map(0 -> flaky)
    )
    val client =
      new IndexedNodeClient(api, maxAttempts = 3, onRetry = (d, a, m) => retries += ((d, a, m)))

    val result = client.unspentBoxesByErgoTree("deadbeef")

    assert(result.map(_.getBoxId()) == Seq("only"))
    assert(flaky.executedCount == 2)
    assert(retries.size == 1)
  }
}
