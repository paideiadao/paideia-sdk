package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.governance.boxes.ProtoDAOBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env

class ProtoDAO(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig): ProtoDAOBox = {
        val res = new ProtoDAOBox(daoConfig)
        res.ctx = ctx
        res.value = 1000000L
        res.tokens = List(
            new ErgoToken(Env.daoTokenId,1L)
        )
        res.contract = contract
        res
    }
}

object ProtoDAO extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): ProtoDAO = 
        getContractInstance[ProtoDAO](contractSignature,new ProtoDAO(contractSignature))
}
