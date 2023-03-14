package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.governance.boxes.ActionUpdateConfigBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfigKey
import im.paideia.Paideia
import im.paideia.common.PaideiaEvent
import im.paideia.common.PaideiaEventResponse
import im.paideia.common.BlockEvent
import org.ergoplatform.appkit.InputBox
import special.collection.Coll
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.governance.transactions.UpdateConfigTransaction

class ActionUpdateConfig (contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(
        ctx: BlockchainContextImpl, 
        proposalId: Int, 
        optionId: Int, 
        activationTime: Long,
        remove: List[DAOConfigKey],
        update: List[(DAOConfigKey, Array[Byte])],
        insert: List[(DAOConfigKey, Array[Byte])]
    ): ActionUpdateConfigBox = {
        ActionUpdateConfigBox(ctx, this, Paideia.getDAO(contractSignature.daoKey),proposalId,optionId,activationTime,remove,update,insert)
    }

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
        val response: PaideiaEventResponse = event match {
            case be: BlockEvent => {
                PaideiaEventResponse.merge(
                    boxes.values.map((b: InputBox) => {
                        if (be.block.getHeader().getTimestamp()>b.getRegisters().get(0).getValue().asInstanceOf[Coll[Long]](3)) {
                            PaideiaEventResponse(1,List(
                                UpdateConfigTransaction(be.ctx,Paideia.getDAO(contractSignature.daoKey),b)
                            ))
                        } else {
                            PaideiaEventResponse(0)
                    }}).toList
                )
            }
            case _ => PaideiaEventResponse(0)
        }
        val superResponse = super.handleEvent(event)
        response
    }

    override lazy val constants: HashMap[String,Object] = {
        val cons = new HashMap[String,Object]()
        cons.put("_IM_PAIDEIA_DAO_KEY",ErgoId.create(contractSignature.daoKey).getBytes())
        cons.put("_IM_PAIDEIA_CONTRACTS_CONFIG",ConfKeys.im_paideia_contracts_config.ergoValue.getValue())
        cons.put("_IM_PAIDEIA_DAO_PROPOSAL_TOKENID",Paideia.getConfig(contractSignature.daoKey).getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid))
        cons
    }
}

object ActionUpdateConfig extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): ActionUpdateConfig = getContractInstance[ActionUpdateConfig](contractSignature,new ActionUpdateConfig(contractSignature))
}