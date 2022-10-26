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

class DAOOrigin(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, dao: DAO, propTokens: Long, voteTokens: Long, actionTokens: Long): DAOOriginBox = {
        val res = new DAOOriginBox(dao.key)
        res.ctx = ctx
        res.value = 1000000L
        res.tokens = List(
            new ErgoToken(Env.daoTokenId,1L),
            new ErgoToken(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid),propTokens),
            new ErgoToken(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_vote_tokenid),voteTokens),
            new ErgoToken(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_action_tokenid),actionTokens)
        )
        res.contract = contract
        res
    }
}

object DAOOrigin extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): DAOOrigin = 
        getContractInstance[DAOOrigin](contractSignature,new DAOOrigin(contractSignature))
}
