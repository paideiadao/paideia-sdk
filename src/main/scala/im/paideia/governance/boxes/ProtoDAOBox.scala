package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import im.paideia.util.Env
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.ProtoDAO

class ProtoDAOBox extends PaideiaBox {
  
}

object ProtoDAOBox {
    def apply(ctx: BlockchainContextImpl): ProtoDAOBox = {
        val res = new ProtoDAOBox
        res.ctx = ctx
        res.value = 1000000L
        res.tokens = List(
            new ErgoToken(Env.daoTokenId,1L)
        )
        res.contract = ProtoDAO(networkType=ctx.getNetworkType()).contract
        res
    }
}