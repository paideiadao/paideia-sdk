package im.paideia.common

import org.ergoplatform.restapi.client.BlockTransactions
import org.ergoplatform.restapi.client.FullBlock
import org.ergoplatform.appkit.impl.BlockchainContextImpl

final case class BlockEvent(_ctx: BlockchainContextImpl, block: FullBlock) extends PaideiaEvent(_ctx)
