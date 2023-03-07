package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox

class MintBox(tokenId: String, mintAmount: Long, tokenName: String, tokenDescription: String, tokenDecimals: Int) extends PaideiaBox {
    override def outBox: OutBox = {
        var b = ctx.newTxBuilder().outBoxBuilder().mintToken(
            new Eip4Token(
                tokenId,
                mintAmount,
                tokenName,
                tokenDescription,
                tokenDecimals
            )
        )
            .value(value)
            .contract(contract)
        b.build()
    }
}
