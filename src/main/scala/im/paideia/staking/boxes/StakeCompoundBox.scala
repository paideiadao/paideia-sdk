package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.StakeCompound
import im.paideia.common.boxes.PaideiaBox

final case class StakeCompoundBox(_ctx: BlockchainContextImpl, useContract: StakeCompound)
  extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract
}
