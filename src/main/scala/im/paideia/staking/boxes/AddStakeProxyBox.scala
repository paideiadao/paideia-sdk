package im.paideia.staking.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAOConfig
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.contracts.AddStakeProxy
import sigmastate.eval.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.util.Env
import org.ergoplatform.appkit.ErgoId

case class AddStakeProxyBox(
  _ctx: BlockchainContextImpl,
  useContract: AddStakeProxy,
  daoConfig: DAOConfig,
  stakeKey: String,
  userAddress: String,
  addAmount: Long
) extends PaideiaBox {

  ctx      = _ctx
  value    = 3500000L
  contract = useContract.contract

  override def registers: List[ErgoValue[_]] = {
    List(
      ErgoValueBuilder.buildFor(
        Colls.fromArray(Address.create(userAddress).toPropositionBytes())
      ),
      ErgoValueBuilder.buildFor(addAmount)
    )
  }

  override def tokens: List[ErgoToken] = {
    val daoTokenId = new ErgoId(daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_tokenid))
    val paideiaTokenId = ErgoId.create(Env.paideiaTokenId)
    if (!daoTokenId.equals(paideiaTokenId))
      List(
        new ErgoToken(stakeKey, 1L),
        new ErgoToken(
          daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),
          addAmount
        ),
        new ErgoToken(Env.paideiaTokenId, Env.defaultBotFee)
      )
    else
      List(
        new ErgoToken(stakeKey, 1L),
        new ErgoToken(
          daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),
          addAmount + Env.defaultBotFee
        )
      )
  }
}
