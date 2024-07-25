package im.paideia.staking.contracts

import scorex.crypto.authds.ADDigest
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import scala.collection.mutable.HashMap
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.ConfKeys
import scorex.crypto.authds.ADDigest
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.staking.boxes.StakeProfitShareBox
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import org.ergoplatform.appkit.InputBox
import sigma.Colls
import im.paideia.DAOConfigKey

class StakeProfitShare(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(ctx: BlockchainContextImpl) = StakeProfitShareBox(ctx, this)

  override lazy val parameters: Map[String, Constant[SType]] = {
    val params = new HashMap[String, Constant[SType]]()
    params.put(
      "imPaideiaDaoKey",
      ByteArrayConstant(ErgoId.create(contractSignature.daoKey).getBytes)
    )
    params.put(
      "stakeStateTokenId",
      ByteArrayConstant(
        Colls.fromArray(
          Paideia
            .getConfig(contractSignature.daoKey)
            .getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid)
        )
      )
    )
    params.toMap
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      val b = StakeProfitShareBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_PROFITSHARE",
      ConfKeys.im_paideia_contracts_staking_profitshare.ergoValue.getValue()
    )
    cons
  }

  override def getConfigContext(configDigest: Option[ADDigest]) = Paideia
    .getConfig(contractSignature.daoKey)
    .getProof(
      ConfKeys.im_paideia_contracts_staking_profitshare
    )(configDigest)
}

object StakeProfitShare extends PaideiaActor {
  override def apply(
    configKey: DAOConfigKey,
    daoKey: String,
    digest: Option[ADDigest] = None
  ): StakeProfitShare =
    contractFromConfig(configKey, daoKey, digest)
  override def apply(contractSignature: PaideiaContractSignature): StakeProfitShare =
    getContractInstance[StakeProfitShare](
      contractSignature,
      new StakeProfitShare(contractSignature)
    )
}
