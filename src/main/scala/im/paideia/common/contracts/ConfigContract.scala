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
import im.paideia.DAO
import java.util.HashMap
import im.paideia.util.ConfKeys

class Config(contractSignature: PaideiaContractSignature) 
    extends PaideiaContract(contractSignature) {
    
    def box(ctx: BlockchainContextImpl, dao: DAO): ConfigBox = {
        val res = new ConfigBox(dao.config)
        res.ctx = ctx
        res.contract = contract
        res.value = 1000000L
        res.tokens = List(new ErgoToken(dao.key,1L))
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

    override lazy val constants: HashMap[String,Object] = {
        val cons = new HashMap[String,Object]()
        cons.put("_IM_PAIDEIA_DAO_ACTION_TOKENID",Paideia.getConfig(contractSignature.daoKey).getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid))
        cons.put("_IM_PAIDEIA_CONTRACTS_CONFIG",ConfKeys.im_paideia_contracts_config.ergoValue.getValue())
        cons
    }
}

object Config extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): Config = 
            getContractInstance[Config](contractSignature,new Config(contractSignature))
}
