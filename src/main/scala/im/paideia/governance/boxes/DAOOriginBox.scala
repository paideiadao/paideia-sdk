package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.ErgoId

class DAOOriginBox(daoKey: String) extends PaideiaBox {
    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValue.of(ErgoId.create(daoKey).getBytes())
        )
    }
}
