package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import im.paideia.util.Env
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import im.paideia.DAO
import org.ergoplatform.appkit.ErgoId

class ProtoDAOBox(dao: DAO) extends PaideiaBox {
    override def registers: List[ErgoValue[_]] = {
        List(
            dao.config._config.ergoValue,
            ErgoValue.of(ErgoId.create(dao.key).getBytes())
        )
    }
}
