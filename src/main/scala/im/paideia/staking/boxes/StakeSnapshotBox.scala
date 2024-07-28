package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.StakeSnapshot
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.InputBox

final case class StakeSnapshotBox(
  _ctx: BlockchainContextImpl,
  useContract: StakeSnapshot,
  _value: Long = 1000000L
) extends PaideiaBox {
  ctx      = _ctx
  value    = _value
  contract = useContract.contract
}

object StakeSnapshotBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): StakeSnapshotBox = {
    val contract =
      StakeSnapshot.getContractInstanceFromTree[StakeSnapshot](inp.getErgoTree())
    StakeSnapshotBox(ctx, contract, inp.getValue())
  }
}
