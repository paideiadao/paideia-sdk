package im.paideia.common.sync

import im.paideia.Paideia
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import org.ergoplatform.appkit.impl.BlockchainContextImpl

/** Drives a [[BlockSource]] forward through a range of confirmed blocks, feeding every
  * transaction as an event to `sink` in block/transaction order - the gap-replay half of
  * bringing a restored (and therefore untrusted - see [[im.paideia.PaideiaSession.restoreState]])
  * checkpoint back up to the chain tip before it is trusted for anything else.
  *
  * Mirrors `PaideiaSyncTask.syncRemainingBlocks`' block loop, minus everything specific to
  * that actor (prefetching, checkpointing policy, mempool lag, Play/akka `Await`): here the
  * loop is synchronous and framework-free, and checkpointing is left entirely to the
  * `onBlock` callback so callers decide their own policy.
  *
  * @constructor
  *   creates a new instance of the ChainSyncer class.
  * @param source
  *   \- where confirmed blocks and the current chain height come from.
  * @param sink
  *   \- handles one event at a time, mirroring how `PaideiaActor`/`Paideia` process a
  *   `TransactionEvent`. Defaults to `Paideia.handleEvent`, i.e. the live session bound to
  *   the calling thread.
  */
class ChainSyncer(
  source: BlockSource,
  sink: PaideiaEvent => PaideiaEventResponse = Paideia.handleEvent(_)
) {

  /** Replays every confirmed block from `fromHeight` to `toHeight` (inclusive), feeding
    * each block's transactions to `sink` in block order. Strict: replay is meant to
    * reproduce exactly what already happened on-chain, so a response carrying any
    * exception is treated as fatal rather than logged and skipped (contrast
    * `syncRemainingBlocks`, which only logs) - the first such exception is (re)thrown and
    * the replay halts immediately, leaving `onBlock` uncalled for the block that failed.
    *
    * @param ctx
    *   \- the blockchain context, threaded through to [[BlockEvents.confirmedEvents]] and
    *   from there to every `TransactionEvent`.
    * @param fromHeight
    *   \- the first height to replay, inclusive.
    * @param toHeight
    *   \- the last height to replay, inclusive.
    * @param onBlock
    *   \- called with a height once every event from that block's transactions has been
    *   handled with no exception - the hook callers use for checkpointing. Defaults to a
    *   no-op.
    * @return
    *   the last height processed; `fromHeight - 1` if the range is empty
    *   (`fromHeight > toHeight`).
    * @throws Throwable
    *   the first exception carried by any event's response, if one occurs.
    */
  def replay(
    ctx: BlockchainContextImpl,
    fromHeight: Int,
    toHeight: Int,
    onBlock: Int => Unit = _ => ()
  ): Int = {
    var lastProcessed = fromHeight - 1
    var height         = fromHeight
    while (height <= toHeight) {
      val block  = source.blockAt(height)
      val events = BlockEvents.confirmedEvents(ctx, block)
      events.foreach { event =>
        val response = sink(event)
        if (response.exceptions.nonEmpty) throw response.exceptions.head
      }
      onBlock(height)
      lastProcessed = height
      height += 1
    }
    lastProcessed
  }

  /** Replays from `fromHeight` until caught up with the chain tip (`source.bestHeight() -
    * lagBehindTip`), re-checking `bestHeight` as the replay approaches it - mirroring the
    * loop-condition/height-refresh style of `PaideiaSyncTask.syncRemainingBlocks`
    * (`nodeHeight = fetchNodeHeight(source)` once the sync catches up to the last-known
    * height), simplified to a single height target instead of a separate virtual-mempool
    * lag window.
    *
    * @param ctx
    *   \- the blockchain context, forwarded to [[replay]].
    * @param fromHeight
    *   \- the first height to replay, inclusive.
    * @param lagBehindTip
    *   \- how many blocks behind the node's reported best height to stop at (0 replays
    *   all the way to the tip).
    * @param onBlock
    *   \- forwarded to [[replay]], called once per height processed.
    * @return
    *   the last height processed; `fromHeight - 1` if already caught up when called.
    */
  def replayToTip(
    ctx: BlockchainContextImpl,
    fromHeight: Int,
    lagBehindTip: Int    = 0,
    onBlock: Int => Unit = _ => ()
  ): Int = {
    var current = fromHeight
    var last    = fromHeight - 1
    var tip     = source.bestHeight() - lagBehindTip
    while (current <= tip) {
      last = replay(ctx, current, current, onBlock)
      current += 1
      if (current > tip) tip = source.bestHeight() - lagBehindTip
    }
    last
  }
}
