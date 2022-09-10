package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import im.paideia.governance.contracts.ProtoDAO

class ProtoDAOProxyBox extends PaideiaBox {
  
}

object ProtoDAOProxyBox {
    def apply(ctx: BlockchainContextImpl, feeBox: PaideiaFeeConfigBox): ProtoDAOProxyBox = {
        val res = new ProtoDAOProxyBox
        res.ctx = ctx
        res.value = 1000000L + feeBox.createDAOFee._1
        res.tokens = if (feeBox.createDAOFee._2 > 0L) 
            List(
                new ErgoToken(Env.paideiaTokenId,feeBox.createDAOFee._2)
            ) 
            else 
                List()
        res.contract = ProtoDAO(networkType=ctx.getNetworkType()).contract
        res
    }
}
