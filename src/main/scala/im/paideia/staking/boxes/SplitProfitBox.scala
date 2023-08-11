package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import org.ergoplatform.sdk.ErgoToken
import im.paideia.staking.contracts.SplitProfit
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigmastate.eval.Colls
import org.ergoplatform.appkit.Address

final case class SplitProfitBox(
  _ctx: BlockchainContextImpl,
  _value: Long,
  _tokens: List[ErgoToken],
  useContract: SplitProfit
) extends PaideiaBox {

  ctx      = _ctx
  value    = _value
  contract = useContract.contract

  override def tokens: List[ErgoToken] = _tokens
}
