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
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.common.events.CreateTransactionsEvent

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
          boxes.values
            .map((b: InputBox) => {
              if (
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
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes())
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
