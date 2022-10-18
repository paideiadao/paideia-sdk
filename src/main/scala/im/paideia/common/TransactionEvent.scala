package im.paideia.common

import org.ergoplatform.restapi.client.ErgoTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl

final case class TransactionEvent(_ctx: BlockchainContextImpl, mempool: Boolean, tx: ErgoTransaction) extends PaideiaEvent(_ctx)
