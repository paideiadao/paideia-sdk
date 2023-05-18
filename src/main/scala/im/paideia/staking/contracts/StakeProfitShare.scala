package im.paideia.staking.contracts

import scorex.crypto.authds.ADDigest
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import scorex.crypto.authds.ADDigest
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.boxes.StakeProfitShareBox

class StakeProfitShare(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(ctx: BlockchainContextImpl) = StakeProfitShareBox(ctx, this)

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes())
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKEN_ID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE",
      ConfKeys.im_paideia_contracts_staking_profitshare.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_PROFIT_TOKENIDS",
      ConfKeys.im_paideia_staking_profit_tokenids.ergoValue.getValue()
    )
    cons
  }

  override def getConfigContext(configDigest: Option[ADDigest]) = Paideia
    .getConfig(contractSignature.daoKey)
    .getProof(
      ConfKeys.im_paideia_staking_state_tokenid,
      ConfKeys.im_paideia_contracts_staking_profitshare,
      ConfKeys.im_paideia_staking_profit_tokenids
    )(configDigest)
}

object StakeProfitShare extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): StakeProfitShare =
    getContractInstance[StakeProfitShare](
      contractSignature,
      new StakeProfitShare(contractSignature)
    )
}
