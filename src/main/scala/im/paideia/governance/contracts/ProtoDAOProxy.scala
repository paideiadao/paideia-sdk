package im.paideia.governance.contracts

import im.paideia.common.contracts._
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.governance.boxes.ProtoDAOProxyBox
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env

class ProtoDAOProxy(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, paideiaDaoConfig: DAOConfig): ProtoDAOProxyBox = {
        val res = new ProtoDAOProxyBox
        res.ctx = ctx
        res.value = 1000000L + paideiaDaoConfig[Long]("im.paideia.createDAOErgFee")
        res.tokens = if (paideiaDaoConfig[Long]("im.paideia.createDAOPaideiaFee") > 0L) 
            List(
                new ErgoToken(Env.paideiaTokenId,paideiaDaoConfig("im.paideia.createDAOPaideiaFee"))
            ) 
            else 
                List()
        res.contract = contract
        res
    }
}

object ProtoDAOProxy extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): ProtoDAOProxy = 
        getContractInstance[ProtoDAOProxy](contractSignature,new ProtoDAOProxy(contractSignature))
}