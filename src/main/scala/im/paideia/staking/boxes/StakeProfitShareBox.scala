package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.StakeProfitShare
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox

final case class StakeProfitShareBox(
  _ctx: BlockchainContextImpl,
  useContract: StakeProfitShare,
  _value: Long = 1000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
}

object StakeProfitShareBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): StakeProfitShareBox = {
    val contract =
      StakeProfitShare.getContractInstanceFromTree[StakeProfitShare](inp.getErgoTree())
    StakeProfitShareBox(ctx, contract, inp.getValue())
  }
}
