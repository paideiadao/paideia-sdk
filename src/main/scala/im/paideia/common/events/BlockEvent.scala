package im.paideia.common.events

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.restapi.client.BlockTransactions
import org.ergoplatform.restapi.client.FullBlock

/** Represents an Paideia event associated with a block.
  *
  * @param ctx
  *   the blockchain context
  * @param block
  *   the full block related to the event
  */
final case class BlockEvent(ctx: BlockchainContextImpl, block: FullBlock)
  extends PaideiaEvent(ctx)
