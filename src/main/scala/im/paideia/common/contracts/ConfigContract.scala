package im.paideia.common.contracts

import im.paideia.common.boxes.ConfigBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.common.{PaideiaEvent, PaideiaEventResponse}

class Config(contractSignature: PaideiaContractSignature) 
    extends PaideiaContract(contractSignature) {
    
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig): ConfigBox = {
        val res = new ConfigBox(daoConfig)
        res.contract = contract
        res
    }
}

object Config extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): Config = 
            getContractInstance[Config](contractSignature)
}
