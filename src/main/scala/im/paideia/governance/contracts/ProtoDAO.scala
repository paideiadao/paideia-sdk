package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.governance.boxes.ProtoDAOBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import java.util.HashMap
import im.paideia.DAOConfigKey
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoValue
import java.nio.charset.StandardCharsets
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.TransactionEvent
import scala.collection.JavaConverters._
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import im.paideia.Paideia
import org.ergoplatform.appkit.impl.InputBoxImpl
import special.sigma.AvlTree
import im.paideia.governance.transactions.MintTransaction
import org.ergoplatform.appkit.Address

class ProtoDAO(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig, value: Long = 1000000L): ProtoDAOBox = {
        val res = new ProtoDAOBox(daoConfig)
        res.ctx = ctx
        res.value = value
        res.tokens = List(
            new ErgoToken(Env.daoTokenId,1L)
        )
        res.contract = contract
        res
    }

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case te: TransactionEvent => {
                PaideiaEventResponse.merge(te.tx.getOutputs().asScala.map{(eto: ErgoTransactionOutput) => {
                    if (eto.getErgoTree()==ergoTree.bytesHex) {
                        val iBox = new InputBoxImpl(eto)
                        val config = Paideia.getConfig(contractSignature.daoKey)
                        if (config._config.ergoAVLTree.digest == iBox.getRegisters().get(0).getValue().asInstanceOf[AvlTree].digest) {
                            val nextTokenToMint = ProtoDAO.tokensToMint.find((s: String) => config._config.lookUp(DAOConfigKey(s)).response(0).tryOp.get == None)
                            PaideiaEventResponse(2,List(MintTransaction(te._ctx,iBox,config,nextTokenToMint.get,Address.create(Env.operatorAddress).getErgoAddress()).unsigned()))
                        } else {
                            PaideiaEventResponse(0)
                        }
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
        cons.put("_IM_PAIDEIA_CONTRACTS_PROTODAO",DAOConfigKey("im.paideia.contracts.protodao").ergoValue.getValue())
        cons.put("_IM_PAIDEIA_CONTRACTS_MINT",DAOConfigKey("im.paideia.contracts.mint").ergoValue.getValue())
        cons.put("_PAIDEIA_DAO_KEY",ErgoId.create(Env.paideiaDaoKey).getBytes())
        cons.put("_IM_PAIDEIA_DAO_NAME",DAOConfigKey("im.paideia.dao.name").ergoValue.getValue())
        cons.put("_IM_PAIDEIA_DAO_VOTE_TOKENID",DAOConfigKey("im.paideia.dao.vote.tokenid").ergoValue.getValue())
        cons.put("_IM_PAIDEIA_DAO_PROPOSAL_TOKENID",DAOConfigKey("im.paideia.dao.proposal.tokenid").ergoValue.getValue())
        cons.put("_IM_PAIDEIA_DAO_ACTION_TOKENID",DAOConfigKey("im.paideia.dao.action.tokenid").ergoValue.getValue())
        cons.put("_VOTE",ErgoValue.of(" Vote".getBytes(StandardCharsets.UTF_8)).getValue())
        cons.put("_PROPOSAL",ErgoValue.of(" Proposal".getBytes(StandardCharsets.UTF_8)).getValue())
        cons.put("_ACTION",ErgoValue.of(" Action".getBytes(StandardCharsets.UTF_8)).getValue())
        cons
    }
}

object ProtoDAO extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): ProtoDAO = 
        getContractInstance[ProtoDAO](contractSignature,new ProtoDAO(contractSignature))

    val tokensToMint = List("im.paideia.dao.vote.tokenid","im.paideia.dao.proposal.tokenid","im.paideia.dao.action.tokenid")
}
