package im.paideia.common.sync

import org.ergoplatform.restapi.client.FullBlock

/** A minimal, framework-free source of full blocks from some blockchain data provider.
  *
  * Implementations are free to fetch blocks however they like (a live node, a fixture
  * file, an in-memory list for tests) - the rest of the sync pipeline only ever needs the
  * current chain height and the ability to fetch a single block by height.
  */
trait BlockSource {

  /** @return
    *   the current full height of the underlying chain source.
    */
  def bestHeight(): Int

  /** @param height
    *   the height of the block to fetch.
    * @return
    *   the full block (header + transactions) at `height`.
    */
  def blockAt(height: Int): FullBlock
}
