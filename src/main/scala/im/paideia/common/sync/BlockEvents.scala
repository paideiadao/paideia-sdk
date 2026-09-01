package im.paideia.common.sync

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.restapi.client.FullBlock
import im.paideia.common.events.TransactionEvent
import scala.collection.JavaConverters._

/** Pure translation of a [[org.ergoplatform.restapi.client.FullBlock]] into the
  * [[im.paideia.common.events.TransactionEvent]]s a Paideia state actor consumes.
  *
  * Does no I/O of its own - block fetching is [[BlockSource]]'s job - so it can be
  * unit-tested directly against a fixture block and reused by any sync driver
  * (`PaideiaSyncTask`'s `syncRemainingBlocks` and `syncMempool` today, an upcoming CLI
  * tomorrow) without duplicating the event-construction logic.
  */
object BlockEvents {

  /** Builds one confirmed [[TransactionEvent]] per transaction in `block`, in block
    * order, mirroring `PaideiaSyncTask.syncRemainingBlocks`.
    *
    * @param ctx
    *   \- the blockchain context.
    * @param block
    *   \- the confirmed block whose transactions should be turned into events.
    * @return
    *   one non-mempool `TransactionEvent` per transaction, at `block`'s height, in the
    *   order the transactions appear in the block.
    */
  def confirmedEvents(
    ctx: BlockchainContextImpl,
    block: FullBlock
  ): Seq[TransactionEvent] =
    block
      .getBlockTransactions()
      .getTransactions()
      .asScala
      .map(tx => TransactionEvent(ctx, false, tx, block.getHeader().getHeight()))
      .toSeq

  /** Builds one virtual-mempool [[TransactionEvent]] per transaction in `block`, in block
    * order, mirroring `PaideiaSyncTask.syncMempool`'s virtual mempool loop - transactions
    * from already-confirmed blocks that are replayed as if they were still in the
    * mempool, so no height is attached.
    *
    * @param ctx
    *   \- the blockchain context.
    * @param block
    *   \- the block whose transactions should be replayed as virtual mempool events.
    * @return
    *   one mempool `TransactionEvent` per transaction, in the order the transactions
    *   appear in the block.
    */
  def virtualMempoolEvents(
    ctx: BlockchainContextImpl,
    block: FullBlock
  ): Seq[TransactionEvent] =
    block
      .getBlockTransactions()
      .getTransactions()
      .asScala
      .map(tx => TransactionEvent(ctx, true, tx))
      .toSeq
}
