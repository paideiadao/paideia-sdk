package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.boxes.ActionSendFundsBasicBox
import im.paideia.Paideia
import special.sigma.Box
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.BlockEvent
import org.ergoplatform.appkit.InputBox
import special.collection.Coll
import im.paideia.governance.transactions.SendFundsBasicTransaction
import java.util.HashMap
import org.ergoplatform.sdk.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.FilterType
import im.paideia.common.filtering.CompareField

class ActionSendFundsBasic(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    proposalId: Int,
    optionId: Int,
    activationTime: Long,
    outputs: Array[Box],
    repeats: Int      = 0,
    repeatDelay: Long = 0L
  ): ActionSendFundsBasicBox = {
    ActionSendFundsBasicBox(
      ctx,
      Paideia.getDAO(contractSignature.daoKey),
      proposalId,
      optionId,
      repeats,
      activationTime,
      repeatDelay,
      outputs,
      this
    )
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        PaideiaEventResponse.merge(
          getUtxoSet
            .map(boxes(_))
            .map((b: InputBox) => {
              val proposalInput = Paideia
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
                )(0)
              if (
                proposalInput
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
                    SendFundsBasicTransaction(
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
    val superResponse = super.handleEvent(event)
    response
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes)
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_TREASURY",
      ConfKeys.im_paideia_contracts_treasury.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_PROPOSAL_TOKENID",
      Paideia
        .getConfig(contractSignature.daoKey)
        .getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid)
    )
    cons
  }
}

object ActionSendFundsBasic extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): ActionSendFundsBasic =
    getContractInstance[ActionSendFundsBasic](
      contractSignature,
      new ActionSendFundsBasic(contractSignature)
    )
}
