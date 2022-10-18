package im.paideia.common.contracts

import org.ergoplatform.appkit.ErgoToken
import im.paideia.common.boxes.TreasuryBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig

class Treasury(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig, value: Long, tokens: List[ErgoToken]): TreasuryBox = {
        val res = new TreasuryBox
        res.ctx = ctx
        res.contract = contract
        res.value = value
        res.tokens = tokens
        res
    }
}

object Treasury extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): Treasury = 
            getContractInstance[Treasury](contractSignature,new Treasury(contractSignature))
}
