package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaActor
import im.paideia.governance.boxes.DAOOriginBox
import im.paideia.DAOConfig
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import im.paideia.util.ConfKeys
import im.paideia.DAO
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ErgoValue
import java.nio.charset.StandardCharsets

class DAOOrigin(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, dao: DAO, propTokens: Long, voteTokens: Long, actionTokens: Long): DAOOriginBox = {
        DAOOriginBox(ctx,dao,propTokens,voteTokens,actionTokens,this)
    }

    override lazy val constants: HashMap[String,Object] = {
        val cons = new HashMap[String,Object]()
        cons.put("_IM_PAIDEIA_CONTRACTS_VOTE",ConfKeys.im_paideia_contracts_vote.ergoValue.getValue())
        cons.put("_IM_PAIDEIA_CONTRACTS_DAO",ConfKeys.im_paideia_contracts_dao.ergoValue.getValue())
        cons.put("_IM_PAIDEIA_STAKING_STATE_TOKENID",ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue())   
        cons.put("_IM_PAIDEIA_DAO_KEY",ErgoId.create(contractSignature.daoKey).getBytes())
        cons.put("_PAIDEIA_DAO_KEY",ErgoId.create(Env.paideiaDaoKey).getBytes())
        cons
    }
}

object DAOOrigin extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): DAOOrigin = 
        getContractInstance[DAOOrigin](contractSignature,new DAOOrigin(contractSignature))
}
