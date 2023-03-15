package im.paideia.common

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.Address

/**
  * A UserRequestEvent represents the event when a user sends a request related to DAO to
  * the PaideiaContract. It extends [[PaideiaEvent]].
  *
  * @constructor Create a new UserRequestEvent with context, user address and DAO key.
  * @param _ctx         The BlockchainContextImpl instance.
  * @param _userAddress The user's address derived from their public key.
  * @param _daoKey      The unique identifier of the Decentralized Autonomous Organization (DAO).
  */
class UserRequestEvent(
  _ctx: BlockchainContextImpl,
  _userAddress: Address,
  _daoKey: String
) extends PaideiaEvent(_ctx)
