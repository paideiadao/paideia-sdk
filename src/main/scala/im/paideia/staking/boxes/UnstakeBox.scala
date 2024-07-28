package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.Unstake
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox

final case class UnstakeBox(
  _ctx: BlockchainContextImpl,
  useContract: Unstake,
  _value: Long = 1000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
}

object UnstakeBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): UnstakeBox = {
    val contract = Unstake.getContractInstanceFromTree[Unstake](inp.getErgoTree())
    UnstakeBox(ctx, contract, inp.getValue())
  }
}
