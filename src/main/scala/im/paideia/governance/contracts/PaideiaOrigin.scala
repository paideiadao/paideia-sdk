package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import im.paideia.DAOConfig
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.boxes.PaideiaOriginBox
import org.ergoplatform.sdk.ErgoToken
import im.paideia.util.Env
import scala.collection.mutable.HashMap
import im.paideia.Paideia
import im.paideia.DAOConfigKey
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.appkit.ErgoValue
import im.paideia.util.ConfKeys
import im.paideia.common.events.{PaideiaEvent, PaideiaEventResponse}
import im.paideia.common.events.TransactionEvent
import im.paideia.DAO
import im.paideia.common.events.UpdateConfigEvent
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import org.ergoplatform.appkit.InputBox

class PaideiaOrigin(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    daoConfig: DAOConfig,
    daoTokensRemaining: Long
  ): PaideiaOriginBox = {
    PaideiaOriginBox(ctx, 1000000L, daoTokensRemaining, this)
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      val b = PaideiaOriginBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons       = new HashMap[String, Object]()
    val paideiaRef = Paideia._daoMap
    cons.put(
      "_IM_PAIDEIA_FEES_CREATEDAO_ERG",
      ErgoValue.of(DAOConfigKey("im.paideia.fees.createdao.erg").hashedKey).getValue()
    )
    cons.put(
      "_IM_PAIDEIA_FEES_CREATEDAO_PAIDEIA",
      ErgoValue.of(DAOConfigKey("im.paideia.fees.createdao.paideia").hashedKey).getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_PROTODAO",
      ErgoValue.of(DAOConfigKey("im.paideia.contracts.protodao").hashedKey).getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_PROTODAOPROXY",
      ErgoValue
        .of(DAOConfigKey("im.paideia.contracts.protodaoproxy").hashedKey)
        .getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT",
      ConfKeys.im_paideia_contracts_split_profit.ergoValue.getValue()
    )
    cons
  }

  override lazy val parameters: Map[String, Constant[SType]] = {
    val cons = new HashMap[String, Constant[SType]]()
    cons.put(
      "paideiaDaoKey",
      ByteArrayConstant(ErgoId.create(Env.paideiaDaoKey).getBytes)
    )
    cons.put(
      "paideiaTokenId",
      ByteArrayConstant(ErgoId.create(Env.paideiaTokenId).getBytes)
    )
    cons.toMap
  }
}

object PaideiaOrigin extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): PaideiaOrigin =
    getContractInstance[PaideiaOrigin](
      contractSignature,
      new PaideiaOrigin(contractSignature)
    )
}
