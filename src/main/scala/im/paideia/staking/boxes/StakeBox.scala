package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.Stake
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox

final case class StakeBox(
  _ctx: BlockchainContextImpl,
  useContract: Stake,
  _value: Long = 1000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
}

object StakeBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): StakeBox = {
    val contract = Stake.getContractInstanceFromTree(inp.getErgoTree())
    StakeBox(ctx, contract, inp.getValue())
  }
}
