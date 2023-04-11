package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.StakeSnapshot
import im.paideia.common.boxes.PaideiaBox

final case class StakeSnapshotBox(_ctx: BlockchainContextImpl, useContract: StakeSnapshot)
  extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract
}
