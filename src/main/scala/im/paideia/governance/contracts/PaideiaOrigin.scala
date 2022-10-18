package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import im.paideia.DAOConfig
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.boxes.PaideiaOriginBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env

class PaideiaOrigin(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig, daoTokensRemaining: Long): PaideiaOriginBox = {
        val res = new PaideiaOriginBox
        res.ctx = ctx
        res.value = 1000000L
        res.tokens = List(
            new ErgoToken(Env.paideiaOriginNFT,1L),
            new ErgoToken(Env.daoTokenId,daoTokensRemaining)
        )
        res.contract = contract
        res
    }
}

object PaideiaOrigin extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): PaideiaOrigin = 
        getContractInstance[PaideiaOrigin](contractSignature,new PaideiaOrigin(contractSignature))
}