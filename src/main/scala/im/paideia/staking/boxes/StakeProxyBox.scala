package im.paideia.staking.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoToken
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.Address
import org.ergoplatform.sdk.ErgoId
import im.paideia.staking.contracts.StakeProxy
import im.paideia.util.ConfKeys
import sigma.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.util.Env

case class StakeProxyBox(
  _ctx: BlockchainContextImpl,
  useContract: StakeProxy,
  daoConfig: DAOConfig,
  userAddress: String,
  stakeAmount: Long
) extends PaideiaBox {

  ctx      = _ctx
  value    = 3500000L
  contract = useContract.contract

  override def registers: List[ErgoValue[_]] = {
    List(
      ErgoValueBuilder.buildFor(
        Colls.fromArray(Address.create(userAddress).toPropositionBytes())
      ),
      ErgoValueBuilder.buildFor(stakeAmount)
    )
  }

  override def tokens: List[ErgoToken] = {
    val daoTokenId = new ErgoId(daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_tokenid))
    val paideiaTokenId = ErgoId.create(Env.paideiaTokenId)
    if (!daoTokenId.equals(paideiaTokenId))
      List(
        new ErgoToken(daoTokenId, stakeAmount),
        new ErgoToken(Env.paideiaTokenId, Env.defaultBotFee)
      )
    else
      List(
        new ErgoToken(
          daoTokenId,
          stakeAmount + Env.defaultBotFee
        )
      )
  }
}
