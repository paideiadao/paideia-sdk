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
import im.paideia.staking.boxes.StakeSnapshotBox
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import org.ergoplatform.appkit.InputBox
import sigma.Colls
import im.paideia.DAOConfigKey

class StakeSnapshot(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(ctx: BlockchainContextImpl) = StakeSnapshotBox(ctx, this)

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

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_STAKING_SNAPSHOT",
      ConfKeys.im_paideia_contracts_staking_snapshot.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION_AMOUNT",
      ConfKeys.im_paideia_staking_emission_amount.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_EMISSION_DELAY",
      ConfKeys.im_paideia_staking_emission_delay.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_CYCLELENGTH",
      ConfKeys.im_paideia_staking_cyclelength.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_WEIGHT_PURE_PARTICIPATION",
      ConfKeys.im_paideia_staking_weight_pureparticipation.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_WEIGHT_PARTICIPATION",
      ConfKeys.im_paideia_staking_weight_participation.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_TOKEN_ID",
      ConfKeys.im_paideia_dao_tokenid.ergoValue.getValue()
    )
    cons
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      val b = StakeSnapshotBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  override def getConfigContext(configDigest: Option[ADDigest]) = Paideia
    .getConfig(contractSignature.daoKey)
    .getProof(
      ConfKeys.im_paideia_contracts_staking_snapshot,
      ConfKeys.im_paideia_staking_emission_amount,
      ConfKeys.im_paideia_staking_emission_delay,
      ConfKeys.im_paideia_staking_cyclelength,
      ConfKeys.im_paideia_staking_weight_pureparticipation,
      ConfKeys.im_paideia_staking_weight_participation,
      ConfKeys.im_paideia_dao_tokenid
    )(configDigest)
}

object StakeSnapshot extends PaideiaActor {
  override def apply(
    configKey: DAOConfigKey,
    daoKey: String,
    digest: Option[ADDigest] = None
  ): StakeSnapshot =
    contractFromConfig(configKey, daoKey, digest)

  override def apply(contractSignature: PaideiaContractSignature): StakeSnapshot =
    getContractInstance[StakeSnapshot](
      contractSignature,
      new StakeSnapshot(contractSignature)
    )
}
