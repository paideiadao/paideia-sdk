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
import java.util.HashMap
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAOConfigKey
import org.ergoplatform.appkit.ErgoId
import java.nio.charset.StandardCharsets

class ProtoDAOProxy(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, 
        paideiaDaoConfig: DAOConfig,
        daoName: String,
        daoGovernanceTokenId: String): ProtoDAOProxyBox = {
        val res = new ProtoDAOProxyBox(daoName,daoGovernanceTokenId)
        res.ctx = ctx
        res.value = 4000000L + paideiaDaoConfig[Long]("im.paideia.fees.createdao.erg")
        res.tokens = if (paideiaDaoConfig[Long]("im.paideia.fees.createdao.paideia") > 0L) 
            List(
                new ErgoToken(Env.paideiaTokenId,paideiaDaoConfig("im.paideia.fees.createdao.paideia"))
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

    override def constants: HashMap[String,Object] = {
        val cons = new HashMap[String,Object]()
        cons.put("_IM_PAIDEIA_CONTRACTS_PROTODAO",ErgoValue.of(DAOConfigKey("im.paideia.contracts.protodao").hashedKey).getValue())
        cons.put("_IM_PAIDEIA_CONTRACTS_MINT",ErgoValue.of(DAOConfigKey("im.paideia.contracts.mint").hashedKey).getValue())
        cons.put("_PAIDEIA_DAO_KEY",ErgoId.create(Env.paideiaDaoKey).getBytes())
        cons.put("_EMPTY_CONFIG_DIGEST",ErgoValue.of(DAOConfig()._config.digest.array).getValue())
        cons.put("_IM_PAIDEIA_DAO_NAME",ErgoValue.of(DAOConfigKey("im.paideia.dao.name").hashedKey).getValue())
        cons.put("_IM_PAIDEIA_DAO_GOVERNANCE_TOKEN_ID",ErgoValue.of(DAOConfigKey("im.paideia.dao.tokenid").hashedKey).getValue())
        cons.put("_IM_PAIDEIA_DAO_KEY",ErgoValue.of(DAOConfigKey("im.paideia.dao.key").hashedKey).getValue())
        cons.put("_DAO_KEY",ErgoValue.of(" DAO Key".getBytes(StandardCharsets.UTF_8)).getValue())
        cons
    }
}

object ProtoDAOProxy extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): ProtoDAOProxy = 
        getContractInstance[ProtoDAOProxy](contractSignature,new ProtoDAOProxy(contractSignature))
}