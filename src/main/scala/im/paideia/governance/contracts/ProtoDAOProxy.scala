package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.governance.boxes.ProtoDAOProxyBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.governance.transactions.CreateProtoDAOTransaction
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.TransactionEvent
import im.paideia.common.PaideiaEvent
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Address

class ProtoDAOProxy(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, paideiaDaoConfig: DAOConfig): ProtoDAOProxyBox = {
        val res = new ProtoDAOProxyBox
        res.ctx = ctx
        res.value = 2000000L + paideiaDaoConfig[Long]("im.paideia.fees.createDAO.erg")
        res.tokens = if (paideiaDaoConfig[Long]("im.paideia.fees.createDAO.paideia") > 0L) 
            List(
                new ErgoToken(Env.paideiaTokenId,paideiaDaoConfig("im.paideia.fees.createDAO.paideia"))
            ) 
            else 
                List()
        res.contract = contract
        res
    }

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case te: TransactionEvent => {
                PaideiaEventResponse.merge(te.tx.getOutputs().asScala.map{(eto: ErgoTransactionOutput) => {
                    if (eto.getErgoTree()==ergoTree.bytesHex) {
                        PaideiaEventResponse(1,List(CreateProtoDAOTransaction(event.ctx,new InputBoxImpl(eto),Address.create(Env.operatorAddress).getErgoAddress).unsigned))
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

object ProtoDAOProxy extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): ProtoDAOProxy = 
        getContractInstance[ProtoDAOProxy](contractSignature,new ProtoDAOProxy(contractSignature))
}