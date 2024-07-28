package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.common.boxes.PaideiaBox
import im.paideia.staking.contracts.StakeVote
import org.ergoplatform.appkit.InputBox

final case class StakeVoteBox(
  _ctx: BlockchainContextImpl,
  useContract: StakeVote,
  _value: Long = 1000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
}

object StakeVoteBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): StakeVoteBox = {
    val contract = StakeVote.getContractInstanceFromTree[StakeVote](inp.getErgoTree())
    StakeVoteBox(ctx, contract, inp.getValue())
  }
}
