package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import org.ergoplatform.sdk.ErgoToken
import im.paideia.staking.contracts.SplitProfit
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigma.Colls
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.InputBox
import scala.collection.JavaConverters._

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

object SplitProfitBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): SplitProfitBox = {
    val contract = SplitProfit.getContractInstanceFromTree[SplitProfit](inp.getErgoTree)
    SplitProfitBox(ctx, inp.getValue, inp.getTokens().asScala.toList, contract)
  }
}
