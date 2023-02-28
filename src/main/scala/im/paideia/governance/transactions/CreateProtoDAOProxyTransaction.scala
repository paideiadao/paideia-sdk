package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl

final case class CreateProtoDAOProxyTransaction(
    _ctx: BlockchainContextImpl,
    
) extends PaideiaTransaction
