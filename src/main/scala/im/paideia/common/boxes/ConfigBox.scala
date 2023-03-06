package im.paideia.common.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.common.contracts.Config
import im.paideia.DAOConfig
import im.paideia.common.contracts.PaideiaContract
import org.ergoplatform.appkit.ErgoValue

class ConfigBox(config: DAOConfig) extends PaideiaBox {
    override def registers: List[ErgoValue[_]] = {
        List(config._config.ergoValue)
    }
}
