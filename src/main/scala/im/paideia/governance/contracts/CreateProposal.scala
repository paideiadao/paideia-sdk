package im.paideia.governance.contracts

import im.paideia.common.contracts._
import im.paideia.governance.boxes.CreateProposalBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import special.sigma.Box
import org.ergoplatform.appkit.Address
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.TransactionEvent
import scala.collection.JavaConverters._
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.governance.transactions.CreateProposalTransaction
import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.util.Env
import java.util.HashMap
import org.ergoplatform.sdk.ErgoId
import im.paideia.common.events.CreateTransactionsEvent
import im.paideia.common.transactions.RefundTransaction
import org.ergoplatform.appkit.NetworkType
import special.collection.Coll

class CreateProposal(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature) {

  def box(
    ctx: BlockchainContextImpl,
    name: String,
    proposalBox: Box,
    actionBoxes: Array[Box],
    voteKey: String,
    userAddress: Address
  ): CreateProposalBox =
    CreateProposalBox(ctx, proposalBox, actionBoxes, voteKey, name, userAddress, this)

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case cte: CreateTransactionsEvent => {
        PaideiaEventResponse.merge(
          getUtxoSet.toList.map { b =>
            PaideiaEventResponse(
              1,
              List(
                if (boxes(b).getCreationHeight() < cte.height - 30) {
                  RefundTransaction(
                    cte.ctx,
                    boxes(b),
                    Address.fromPropositionBytes(
                      NetworkType.MAINNET,
                      boxes(b)
                        .getRegisters()
                        .get(0)
                        .getValue()
                        .asInstanceOf[Coll[Coll[Byte]]](0)
                        .toArray
                    )
                  )
                } else {
                  CreateProposalTransaction(
                    cte.ctx,
                    boxes(b),
                    Address.create(Env.operatorAddress)
                  )
                }
              )
            )

          }.toList
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
    cons
  }
}

object CreateProposal extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): CreateProposal =
    getContractInstance(contractSignature, new CreateProposal(contractSignature))
}
