package im.paideia.common

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.Address

class UserRequestEvent(_ctx: BlockchainContextImpl, _userAddress: Address, _daoKey: String) extends PaideiaEvent(_ctx) {
  
}
