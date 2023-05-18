package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.common.boxes.PaideiaBox
import im.paideia.staking.contracts.StakeVote

final case class StakeVoteBox(_ctx: BlockchainContextImpl, useContract: StakeVote)
  extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract
}
