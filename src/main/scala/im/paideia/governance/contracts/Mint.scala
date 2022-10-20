package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaActor
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.boxes.MintBox

class Mint(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl,tokenId: String, mintAmount: Long, tokenName: String, tokenDescription: String, tokenDecimals: Int): MintBox = {
        val res = new MintBox(tokenId,mintAmount,tokenName,tokenDescription,tokenDecimals)
        res.ctx = ctx
        res.value = 1000000L
        res.contract = contract
        res
    }
}

object Mint extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): Mint = 
        getContractInstance[Mint](contractSignature,new Mint(contractSignature))
}
