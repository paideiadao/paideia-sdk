package im.paideia.common.sync

import org.scalatest.funsuite.AnyFunSuite
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.restapi.client.FullBlock
import com.google.gson.Gson
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent

import scala.collection.mutable

/** Covers `ChainSyncer.replay`/`replayToTip` against a stub [[BlockSource]] and a
  * recording sink - no real node, no live `Paideia` session. Blocks are built inline (via
  * the same JSON shape as `response_FullBlock.json`) rather than as extra fixture files,
  * since every test here needs a different number of blocks/transactions.
  */
class ChainSyncerSuite extends AnyFunSuite {

  // ChainSyncer/BlockEvents only ever thread ctx through into TransactionEvent - it's
  // never dereferenced - so a null stand-in is enough here (mirrors BlockEventsSuite).
  private val ctx: BlockchainContextImpl = null.asInstanceOf[BlockchainContextImpl]

  private def fullBlock(height: Int, txIds: Seq[String]): FullBlock = {
    val txsJson = txIds
      .map(id => s"""{"id":"$id","inputs":[],"dataInputs":[],"outputs":[]}""")
      .mkString(",")
    val json =
      s"""{
         |  "header": {"id": "header$height", "height": $height},
         |  "blockTransactions": {
         |    "headerId": "header$height",
         |    "transactions": [$txsJson]
         |  }
         |}""".stripMargin
    new Gson().fromJson(json, classOf[FullBlock])
  }

  private def txIdsFor(height: Int): Seq[String] = Seq(s"tx${height}a", s"tx${height}b")

  /** @param bestHeights
    *   \- successive `bestHeight()` results; the last value repeats once exhausted (models
    *   a node whose reported height grows a fixed number of times and then holds steady).
    */
  private class StubBlockSource(blocks: Map[Int, FullBlock], bestHeights: Seq[Int])
    extends BlockSource {
    private var bestHeightCalls = 0

    override def bestHeight(): Int = {
      val h = bestHeights(math.min(bestHeightCalls, bestHeights.size - 1))
      bestHeightCalls += 1
      h
    }

    override def blockAt(height: Int): FullBlock =
      blocks.getOrElse(height, throw new NoSuchElementException(s"unexpected blockAt($height)"))
  }

  test(
    "replay feeds events in block and transaction order, calls onBlock per height in order, and returns the last height processed"
  ) {
    val blocks  = (1 to 3).map(h => h -> fullBlock(h, txIdsFor(h))).toMap
    val source  = new StubBlockSource(blocks, Seq(3))
    val received = mutable.Buffer[(Int, String)]()
    val onBlocks = mutable.Buffer[Int]()
    val sink: PaideiaEvent => PaideiaEventResponse = {
      case te: TransactionEvent =>
        received += ((te.height, te.tx.getId()))
        PaideiaEventResponse(1)
      case _ => PaideiaEventResponse(0)
    }
    val syncer = new ChainSyncer(source, sink)

    val last = syncer.replay(ctx, fromHeight = 1, toHeight = 3, onBlock = h => onBlocks += h)

    assert(last == 3)
    assert(onBlocks == Seq(1, 2, 3))
    assert(
      received == Seq(
        (1, "tx1a"),
        (1, "tx1b"),
        (2, "tx2a"),
        (2, "tx2b"),
        (3, "tx3a"),
        (3, "tx3b")
      )
    )
  }

  test("replay over an empty range fetches nothing and returns fromHeight - 1") {
    val source = new StubBlockSource(Map.empty, Seq(0))
    val syncer = new ChainSyncer(source, _ => PaideiaEventResponse(0))

    assert(syncer.replay(ctx, fromHeight = 5, toHeight = 4) == 4)
  }

  test("replay throws the first exception carried by a response and halts before later blocks") {
    val blocks   = (1 to 3).map(h => h -> fullBlock(h, txIdsFor(h))).toMap
    val source   = new StubBlockSource(blocks, Seq(3))
    val boom     = new RuntimeException("bad tx")
    val seen     = mutable.Buffer[String]()
    val onBlocks = mutable.Buffer[Int]()
    val sink: PaideiaEvent => PaideiaEventResponse = {
      case te: TransactionEvent =>
        seen += te.tx.getId()
        if (te.tx.getId() == "tx2b") PaideiaEventResponse(-1, exceptions = List(boom))
        else PaideiaEventResponse(1)
      case _ => PaideiaEventResponse(0)
    }
    val syncer = new ChainSyncer(source, sink)

    val ex = intercept[RuntimeException] {
      syncer.replay(ctx, fromHeight = 1, toHeight = 3, onBlock = h => onBlocks += h)
    }

    assert(ex eq boom)
    // tx2b's event was fed to the sink (its exception is what halted things), but block 2
    // never finished - onBlock(2) is never called - and block 3 is never fetched at all.
    assert(seen == Seq("tx1a", "tx1b", "tx2a", "tx2b"))
    assert(onBlocks == Seq(1))
  }

  test("replayToTip refreshes bestHeight as it approaches and respects lagBehindTip") {
    val blocks   = (1 to 6).map(h => h -> fullBlock(h, txIdsFor(h))).toMap
    // bestHeight grows once: the first read sees 3, every read after sees 5.
    val source   = new StubBlockSource(blocks, Seq(3, 5))
    val onBlocks = mutable.Buffer[Int]()
    val syncer   = new ChainSyncer(source, _ => PaideiaEventResponse(0))

    val last =
      syncer.replayToTip(ctx, fromHeight = 1, lagBehindTip = 1, onBlock = h => onBlocks += h)

    // First tip = 3 - 1 = 2; once caught up to it, bestHeight is re-read and the tip
    // becomes 5 - 1 = 4.
    assert(onBlocks == Seq(1, 2, 3, 4))
    assert(last == 4)
  }

  test("replayToTip does nothing and returns fromHeight - 1 when already caught up") {
    val source = new StubBlockSource(Map.empty, Seq(0))
    val syncer = new ChainSyncer(source, _ => PaideiaEventResponse(0))

    assert(syncer.replayToTip(ctx, fromHeight = 1, lagBehindTip = 0) == 0)
  }
}
