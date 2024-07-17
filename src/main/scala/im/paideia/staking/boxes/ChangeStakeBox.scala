package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.ChangeStake
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox

final case class ChangeStakeBox(
  _ctx: BlockchainContextImpl,
  useContract: ChangeStake,
  _value: Long = 1000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
}

object ChangeStakeBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ChangeStakeBox = {
    val contract = ChangeStake.getContractInstanceFromTree(inp.getErgoTree())
    ChangeStakeBox(ctx, contract, inp.getValue())
  }
}
