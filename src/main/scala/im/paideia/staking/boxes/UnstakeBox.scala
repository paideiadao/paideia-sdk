package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.Unstake
import im.paideia.common.boxes.PaideiaBox

final case class UnstakeBox(_ctx: BlockchainContextImpl, useContract: Unstake)
  extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract
}
