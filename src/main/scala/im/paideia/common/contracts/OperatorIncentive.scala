package im.paideia.common.contracts

import im.paideia.common.boxes.OperatorIncentiveBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl

class OperatorIncentive(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, value: Long, paideiaTokens: Long): OperatorIncentiveBox = {
        OperatorIncentiveBox(ctx,value,paideiaTokens,this)
    }
}

object OperatorIncentive extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): OperatorIncentive = 
        getContractInstance[OperatorIncentive](contractSignature,new OperatorIncentive(contractSignature))
}
