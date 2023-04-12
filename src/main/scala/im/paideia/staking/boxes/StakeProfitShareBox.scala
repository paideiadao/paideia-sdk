package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.StakeProfitShare
import im.paideia.common.boxes.PaideiaBox

final case class StakeProfitShareBox(
  _ctx: BlockchainContextImpl,
  useContract: StakeProfitShare
) extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract
}
