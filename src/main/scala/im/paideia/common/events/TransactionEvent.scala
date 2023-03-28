package im.paideia.common.events

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.restapi.client.ErgoTransaction

/**
  * An Event that represents a transaction either in the mempool or included in a block on the blockchain.
  *
  * @param ctx      The blockchain context.
  * @param mempool  a boolean indicating whether the transaction is in the mempool or not.
  * @param tx       the [ErgoTransaction] instance representing the transaction.
 **/
final case class TransactionEvent(
  ctx: BlockchainContextImpl,
  mempool: Boolean,
  tx: ErgoTransaction,
  height: Int = 0
) extends PaideiaEvent(ctx)
