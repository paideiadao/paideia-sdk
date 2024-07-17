package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.StakeCompound
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox

final case class StakeCompoundBox(
  _ctx: BlockchainContextImpl,
  useContract: StakeCompound,
  _value: Long = 1000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
}

object StakeCompoundBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): StakeCompoundBox = {
    val contract = StakeCompound.getContractInstanceFromTree(inp.getErgoTree())
    StakeCompoundBox(ctx, contract, inp.getValue())
  }
}
