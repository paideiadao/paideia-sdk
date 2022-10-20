package im.paideia.common.contracts

import im.paideia.common.boxes.ConfigBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.common.{PaideiaEvent, PaideiaEventResponse}
import im.paideia.common.TransactionEvent
import im.paideia.Paideia
import org.ergoplatform.restapi.client.ErgoTransactionInput
import scala.collection.JavaConverters._
import org.ergoplatform.appkit.ErgoToken

class Config(contractSignature: PaideiaContractSignature) 
    extends PaideiaContract(contractSignature) {
    
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig): ConfigBox = {
        val res = new ConfigBox(daoConfig)
        res.ctx = ctx
        res.contract = contract
        res.value = 1000000L
        res.tokens = List(new ErgoToken(daoConfig[Array[Any]]("im.paideia.dao.key").map(_.asInstanceOf[Byte]),1L))
        res
    }

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case te: TransactionEvent => {
                val currentUtxos = getUtxoSet
                PaideiaEventResponse.merge(te.tx.getInputs().asScala.map{(eti: ErgoTransactionInput) => {
                    if (currentUtxos.contains(eti.getBoxId())) {
                        Paideia._daoMap(boxes(eti.getBoxId()).getTokens().get(0).getId().toString).config.handleExtension(eti.getExtension().asScala)
                        PaideiaEventResponse(2)
                    } else {
                        PaideiaEventResponse(0)
                    }
                }}.toList)
            }
            case _ => PaideiaEventResponse(0)
        }
        val superResponse = super.handleEvent(event)
        PaideiaEventResponse.merge(List(response,superResponse))
    }
}

object Config extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): Config = 
            getContractInstance[Config](contractSignature,new Config(contractSignature))
}
