package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import im.paideia.util.Env
import org.ergoplatform.appkit.ErgoToken
import im.paideia.governance.contracts.PaideiaOrigin
import org.ergoplatform.appkit.impl.BlockchainContextImpl

class PaideiaOriginBox extends PaideiaBox

object PaideiaOriginBox {
    def apply(ctx: BlockchainContextImpl, daoTokens: Long): PaideiaOriginBox = {
        val res = new PaideiaOriginBox
        res.ctx = ctx
        res.value = 1000000L
        res.tokens = List(
            new ErgoToken(Env.daoTokenId,daoTokens)
        )
        res.contract = PaideiaOrigin(networkType=ctx.getNetworkType()).contract
        res
    }
}
