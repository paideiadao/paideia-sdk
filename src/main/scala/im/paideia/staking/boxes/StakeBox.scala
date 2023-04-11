package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.Stake
import im.paideia.common.boxes.PaideiaBox

final case class StakeBox(_ctx: BlockchainContextImpl, useContract: Stake)
  extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract
}
