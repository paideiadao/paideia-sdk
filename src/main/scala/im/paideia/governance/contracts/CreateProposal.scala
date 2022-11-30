package im.paideia.governance.contracts

import im.paideia.common.contracts._
import im.paideia.governance.boxes.CreateProposalBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import special.sigma.Box
import org.ergoplatform.appkit.Address
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.PaideiaEvent
import im.paideia.common.TransactionEvent
import scala.collection.JavaConverters._
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.governance.transactions.CreateProposalTransaction
import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.util.Env

class CreateProposal(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, proposalBox: Box, actionBoxes: Array[Box], voteKey: String, userAddress: Address): CreateProposalBox = 
        CreateProposalBox(ctx,proposalBox,actionBoxes,voteKey,userAddress,this)

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case te: TransactionEvent => {
                PaideiaEventResponse.merge(te.tx.getOutputs().asScala.map{(eto: ErgoTransactionOutput) => {
                    if (eto.getErgoTree()==ergoTree.bytesHex) {
                        PaideiaEventResponse(1,List(CreateProposalTransaction(te._ctx,new InputBoxImpl(eto),Address.create(Env.operatorAddress)).unsigned))
                    } else {
                        PaideiaEventResponse(0)
                    }
                }}.toList)
            }
            case _ => PaideiaEventResponse(0)
        }
        val superResponse = super.handleEvent(event)
        response
    }
}

object CreateProposal extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): CreateProposal = getContractInstance(contractSignature, new CreateProposal(contractSignature))
}