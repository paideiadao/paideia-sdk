package im.paideia.governance.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.CreateDAO
import im.paideia.common.boxes.PaideiaBox

final case class CreateDAOBox(
  _ctx: BlockchainContextImpl,
  useContract: CreateDAO,
  _value: Long = 2000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
}
