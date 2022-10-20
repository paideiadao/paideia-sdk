package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import im.paideia.util.Env
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue

class ProtoDAOBox(daoConfig: DAOConfig) extends PaideiaBox {
    override def registers: List[ErgoValue[_]] = {
        List(
            daoConfig._config.ergoValue
        )
    }
}
