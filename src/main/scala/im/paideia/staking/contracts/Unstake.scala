package im.paideia.staking.contracts

import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import java.util.HashMap
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.ConfKeys
import scorex.crypto.authds.ADDigest
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.boxes.UnstakeBox

class Unstake(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(ctx: BlockchainContextImpl) = UnstakeBox(ctx, this)

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes)
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKEN_ID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_UNSTAKE",
      ConfKeys.im_paideia_contracts_staking_unstake.ergoValue.getValue()
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
      ConfKeys.im_paideia_contracts_staking_unstake,
      ConfKeys.im_paideia_staking_profit_tokenids
    )(configDigest)
}

object Unstake extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): Unstake =
    getContractInstance[Unstake](
      contractSignature,
      new Unstake(contractSignature)
    )
}
