package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.governance.boxes.DAOOriginBox
import im.paideia.DAOConfig
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoToken
import im.paideia.util.Env
import im.paideia.util.ConfKeys
import im.paideia.DAO
import scala.collection.mutable.HashMap
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.appkit.ErgoValue
import java.nio.charset.StandardCharsets
import sigma.Colls
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import im.paideia.common.events.PaideiaEvent
import im.paideia.Paideia
import sigma.Coll
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ByteArrayConstant
import org.ergoplatform.appkit.InputBox

class DAOOrigin(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(
    contractSignature,
    ConfKeys.im_paideia_contracts_dao.originalKey
  ) {
  def box(
    ctx: BlockchainContextImpl,
    dao: DAO,
    propTokens: Long,
    actionTokens: Long
  ): DAOOriginBox = {
    DAOOriginBox(ctx, dao, propTokens, actionTokens, this)
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      val b = DAOOriginBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case te: TransactionEvent => {
        if (!te.mempool && getUtxoSet.contains(te.tx.getInputs().get(0).getBoxId())) {
          val proposalIndex =
            Long.MaxValue - te.tx.getOutputs().get(0).getAssets().get(1).getAmount() - 1
          val proposalName = new String(
            ErgoValue
              .fromHex(te.tx.getOutputs().get(1).getAdditionalRegisters().get("R7"))
              .getValue()
              .asInstanceOf[Coll[Byte]]
              .toArray,
            StandardCharsets.UTF_8
          )
          Paideia
            .getDAO(
              new ErgoId(
                ErgoValue
                  .fromHex(te.tx.getOutputs().get(0).getAdditionalRegisters().get("R4"))
                  .getValue()
                  .asInstanceOf[Coll[Byte]]
                  .toArray
              ).toString()
            )
            .newProposal(proposalIndex.toInt, proposalName)
          PaideiaEventResponse(1)
        } else {
          PaideiaEventResponse(0)
        }
      }
      case _ => PaideiaEventResponse(0)
    }
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }

  override lazy val parameters: Map[String, Constant[SType]] = {
    val params = new HashMap[String, Constant[SType]]()
    params.put(
      "imPaideiaDaoKey",
      ByteArrayConstant(ErgoId.create(contractSignature.daoKey).getBytes)
    )
    params.put(
      "paideiaDaoKey",
      ByteArrayConstant(ErgoId.create(Env.paideiaDaoKey).getBytes)
    )
    params.put(
      "paideiaTokenId",
      ByteArrayConstant(ErgoId.create(Env.paideiaTokenId).getBytes)
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
      "_IM_PAIDEIA_CONTRACTS_DAO",
      ConfKeys.im_paideia_contracts_dao.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_FEES_CREATEPROPOSAL_PAIDEIA",
      ConfKeys.im_paideia_fees_createproposal_paideia.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_PROPOSAL",
      Colls.fromArray(
        ConfKeys.im_paideia_contracts_proposal(Array[Byte]()).originalKeyBytes
      )
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_ACTION",
      Colls.fromArray(
        ConfKeys.im_paideia_contracts_action(Array[Byte]()).originalKeyBytes
      )
    )
    cons.put(
      "_IM_PAIDEIA_DAO_MIN_PROPOSAL_TIME",
      ConfKeys.im_paideia_dao_min_proposal_time.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_MIN_STAKE_PROPOSAL",
      ConfKeys.im_paideia_dao_min_stake_proposal.ergoValue.getValue()
    )
    cons
  }
}

object DAOOrigin extends PaideiaActor {
  override def apply(contractSignature: PaideiaContractSignature): DAOOrigin =
    getContractInstance[DAOOrigin](contractSignature, new DAOOrigin(contractSignature))
}
