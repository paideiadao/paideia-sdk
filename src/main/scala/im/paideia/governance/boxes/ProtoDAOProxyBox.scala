package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import im.paideia.governance.contracts.ProtoDAO
import im.paideia.common.boxes.ConfigBox
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.DAOConfigValue
import im.paideia.DAOConfigValueSerializer
import org.ergoplatform.appkit.ErgoId
import special.collection.Coll
import org.ergoplatform.appkit.ErgoType
import special.collection.CollOverArray

class ProtoDAOProxyBox(daoName: String, daoGovernanceTokenId: String) extends PaideiaBox {
    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValue.of(Array(
                    ErgoValue.of(DAOConfigValueSerializer(daoName)).getValue(),
                    ErgoValue.of(DAOConfigValueSerializer(ErgoId.create(daoGovernanceTokenId).getBytes().asInstanceOf[Array[Byte]])).getValue())
            ,ErgoType.collType(ErgoType.byteType()))
        )
    }
}

