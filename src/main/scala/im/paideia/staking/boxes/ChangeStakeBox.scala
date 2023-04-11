package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.ChangeStake
import im.paideia.common.boxes.PaideiaBox

final case class ChangeStakeBox(_ctx: BlockchainContextImpl, useContract: ChangeStake)
  extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract
}
