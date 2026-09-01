package im.paideia.common.sync

import org.scalatest.funsuite.AnyFunSuite
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.restapi.client.FullBlock
import com.google.gson.Gson
import im.paideia.HttpClientTesting

/** Covers `BlockEvents`' pure translation of a `FullBlock` fixture (3 transactions, no
  * I/O involved) into `TransactionEvent`s: event count, transaction order, the mempool
  * flag, and height propagation for confirmed events vs. its absence for virtual mempool
  * events.
  */
class BlockEventsSuite extends AnyFunSuite with HttpClientTesting {

  // TransactionEvent only ever stores ctx as a field - it's never dereferenced during
  // construction or by BlockEvents - so a null stand-in is enough here.
  private val ctx: BlockchainContextImpl = null.asInstanceOf[BlockchainContextImpl]

  private def loadFixtureBlock(): FullBlock =
    new Gson().fromJson(loadNodeResponse("response_FullBlock.json"), classOf[FullBlock])

  test(
    "confirmedEvents builds one non-mempool event per transaction, in block order, at the block's height"
  ) {
    val block  = loadFixtureBlock()
    val events = BlockEvents.confirmedEvents(ctx, block)

    assert(events.size == 3)
    assert(
      events.map(_.tx.getId()) == Seq(
        "tx1111111111111111111111111111111111111111111111111111111111",
        "tx2222222222222222222222222222222222222222222222222222222222",
        "tx3333333333333333333333333333333333333333333333333333333333"
      )
    )
    assert(events.forall(_.mempool == false))
    assert(events.forall(_.height == 800000))
    assert(events.forall(_.ctx == ctx))
  }

  test(
    "virtualMempoolEvents builds one mempool event per transaction, in block order, with no height"
  ) {
    val block  = loadFixtureBlock()
    val events = BlockEvents.virtualMempoolEvents(ctx, block)

    assert(events.size == 3)
    assert(
      events.map(_.tx.getId()) == Seq(
        "tx1111111111111111111111111111111111111111111111111111111111",
        "tx2222222222222222222222222222222222222222222222222222222222",
        "tx3333333333333333333333333333333333333333333333333333333333"
      )
    )
    assert(events.forall(_.mempool == true))
    // No height arg is passed for virtual mempool events - TransactionEvent's default.
    assert(events.forall(_.height == 0))
  }
}
