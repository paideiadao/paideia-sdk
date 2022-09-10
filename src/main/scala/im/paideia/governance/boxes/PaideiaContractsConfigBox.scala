package im.paideia.governance.boxes

import im.paideia.common.boxes.ConfigBox
import org.ergoplatform.appkit.ErgoType
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import im.paideia.governance.contracts._
import im.paideia.common.contracts._

class PaideiaContractsConfigBox(_ctx: BlockchainContextImpl, val protoDAOContract: ProtoDAO) extends ConfigBox(_ctx,12L) {
    override def registers = List[ErgoValue[?]](
        ErgoValue.of(Array(
            configIndex
        ).map(java.lang.Long.valueOf),ErgoType.longType()),
        ErgoValue.of(List[PaideiaContract](protoDAOContract).map((c: PaideiaContract) => c.ergoValue.getValue).toArray,
            ErgoType.pairType(
                ErgoType.collType(ErgoType.byteType()),
                ErgoType.pairType(
                    ErgoType.collType(ErgoType.byteType()),
                    ErgoType.collType(ErgoType.byteType())
                )))
    )
}

object PaideiaContractsConfigBox {
    def apply(ctx: BlockchainContextImpl, protoDAOContract: ProtoDAO) = {
        val res = new PaideiaContractsConfigBox(ctx,protoDAOContract)
        res.tokens = List(
            new ErgoToken(Env.configTokenId,1L)
        )
        res
    }
}
