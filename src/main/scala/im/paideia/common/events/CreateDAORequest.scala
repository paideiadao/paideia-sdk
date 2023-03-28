package im.paideia.common.events

import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.impl.BlockchainContextImpl

/**
  * A case class representing a request to create a DAO.
  *
  * @constructor Create a new instance of CreateDAORequest
  * @param ctx          The blockchain context to use for the creation of the DAO.
  * @param userAddress  The address of the user.
  */
final case class CreateDAORequest(ctx: BlockchainContextImpl, userAddress: Address)
  extends UserRequestEvent(ctx, userAddress, "")
