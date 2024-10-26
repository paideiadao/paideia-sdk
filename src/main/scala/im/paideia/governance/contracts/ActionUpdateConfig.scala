package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.governance.boxes.ActionUpdateConfigBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfigKey
import im.paideia.Paideia
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.BlockEvent
import org.ergoplatform.appkit.InputBox
import sigma.Coll
import scala.collection.mutable.HashMap
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.governance.transactions.UpdateConfigTransaction
import im.paideia.common.events.TransactionEvent
import scala.collection.JavaConverters._
import im.paideia.common.events.UpdateConfigEvent
import im.paideia.common.boxes.ConfigBox
import im.paideia.common.contracts.Config
import sigma.AvlTree
import scorex.crypto.authds.ADDigest
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.common.filtering.CompareField
import sigma.ast.Constant
import sigma.ast.SType
import sigma.ast.ConstantPlaceholder
import sigma.ast.ByteArrayConstant
import sigma.Colls
import sigma.ast.SCollection
import sigma.ast.SByte

class ActionUpdateConfig(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(
    contractSignature,
    garbageCollectable = Some(
      Array(
        new ErgoId(
          Paideia
            .getConfig(contractSignature.daoKey)
            .getArray(ConfKeys.im_paideia_dao_action_tokenid)
        )
      )
    )
  ) {

  def box(
    ctx: BlockchainContextImpl,
    proposalId: Int,
    optionId: Int,
    activationTime: Long,
    remove: List[DAOConfigKey],
    update: List[(DAOConfigKey, Array[Byte])],
    insert: List[(DAOConfigKey, Array[Byte])]
  ): ActionUpdateConfigBox = {
    ActionUpdateConfigBox(
      ctx,
      this,
      Paideia.getDAO(contractSignature.daoKey),
      proposalId,
      optionId,
      activationTime,
      remove,
      update,
      insert
    )
  }

  override def validateBox(ctx: BlockchainContextImpl, inputBox: InputBox): Boolean = {
    if (inputBox.getErgoTree().bytesHex != ergoTree.bytesHex) return false
    try {
      val b = ActionUpdateConfigBox.fromInputBox(ctx, inputBox)
      true
    } catch {
      case _: Throwable => false
    }
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        PaideiaEventResponse.merge(
          getUtxoSet
            .map(boxes(_))
            .map((b: InputBox) => {
              val proposalInputs = Paideia
                .getBox(
                  new FilterLeaf[String](
                    FilterType.FTEQ,
                    new ErgoId(
                      Paideia
                        .getConfig(contractSignature.daoKey)
                        .getArray(ConfKeys.im_paideia_dao_proposal_tokenid)
                    )
                      .toString(),
                    CompareField.ASSET,
                    0
                  )
                )
                .filter((box: InputBox) =>
                  box
                    .getRegisters()
                    .get(0)
                    .getValue()
                    .asInstanceOf[Coll[Int]](0) == b
                    .getRegisters()
                    .get(0)
                    .getValue()
                    .asInstanceOf[Coll[Long]](0)
                    .toInt
                )
              if (
                proposalInputs.size > 0 &&
                proposalInputs(0)
                  .getRegisters()
                  .get(0)
                  .getValue()
                  .asInstanceOf[Coll[Int]](1) == b
                  .getRegisters()
                  .get(0)
                  .getValue()
                  .asInstanceOf[Coll[Long]](1)
                  .toInt &&
                cte.currentTime > b
                  .getRegisters()
                  .get(0)
                  .getValue()
                  .asInstanceOf[Coll[Long]](3)
              ) {
                PaideiaEventResponse(
                  1,
                  List(
                    UpdateConfigTransaction(
                      cte.ctx,
                      Paideia.getDAO(contractSignature.daoKey),
                      b
                    )
                  )
                )
              } else {
                PaideiaEventResponse(0)
              }
            })
            .toList
        )
      }
      case _ => PaideiaEventResponse(0)
    }
    PaideiaEventResponse.merge(List(super.handleEvent(event), response))
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_CONFIG",
      ConfKeys.im_paideia_contracts_config.ergoValue.getValue()
    )
    cons
  }

  override lazy val parameters: Map[String, Constant[SType]] = {
    val params = new scala.collection.mutable.HashMap[String, Constant[SType]]()
    params.put(
      "imPaideiaDaoKey",
      ByteArrayConstant(Colls.fromArray(ErgoId.create(contractSignature.daoKey).getBytes))
    )
    params.put(
      "imPaideiaDaoProposalTokenId",
      ByteArrayConstant(
        Paideia
          .getConfig(contractSignature.daoKey)
          .getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)
      )
    )
    params.toMap
  }
}

object ActionUpdateConfig extends PaideiaActor {
  override def apply(
    configKey: DAOConfigKey,
    daoKey: String,
    digest: Option[ADDigest] = None
  ): ActionUpdateConfig =
    contractFromConfig[ActionUpdateConfig](configKey, daoKey, digest)
  override def apply(contractSignature: PaideiaContractSignature): ActionUpdateConfig =
    getContractInstance[ActionUpdateConfig](
      contractSignature,
      new ActionUpdateConfig(contractSignature)
    )
}
