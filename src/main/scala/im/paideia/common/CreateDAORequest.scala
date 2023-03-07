package im.paideia.common

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.Address

final case class CreateDAORequest(ctx: BlockchainContextImpl, userAddress: Address) extends UserRequestEvent(ctx,userAddress,"")
