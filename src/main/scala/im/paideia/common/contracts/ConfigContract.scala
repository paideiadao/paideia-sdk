package im.paideia.common.contracts

import im.paideia.common.boxes.ConfigBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.common.{PaideiaEvent, PaideiaEventResponse}
import im.paideia.common.TransactionEvent
import im.paideia.Paideia
import org.ergoplatform.restapi.client.ErgoTransactionInput
import scala.collection.JavaConverters._

class Config(contractSignature: PaideiaContractSignature) 
    extends PaideiaContract(contractSignature) {
    
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig): ConfigBox = {
        val res = new ConfigBox(daoConfig)
        res.contract = contract
        res
    }

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case te: TransactionEvent => {
                val currentUtxos = getUtxoSet
                te.tx.getInputs().asScala.map{(eti: ErgoTransactionInput) => {
                    if (currentUtxos.contains(eti.getBoxId())) {
                        Paideia._daoMap(boxes(eti.getBoxId()).getAssets().get(0).getTokenId()).config.handleExtension(eti.getExtension().asScala)
                        PaideiaEventResponse(2)
                    } else {
                        PaideiaEventResponse(0)
                    }
                }}.max
            }
            case _ => PaideiaEventResponse(0)
        }
        val superResponse = super.handleEvent(event)
        response
    }
}

object Config extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): Config = 
            getContractInstance[Config](contractSignature,new Config(contractSignature))
}
