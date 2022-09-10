package im.paideia.governance.boxes

import im.paideia.common.boxes.ConfigBox
import org.ergoplatform.appkit.ErgoType
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env

class PaideiaFeeConfigBox(_ctx: BlockchainContextImpl, val profitSharePct: Long,val createDAOFee: (Long,Long), val createVoteFee: (Long,Long), val createProposalFee: (Long,Long), val castVoteFee: (Long,Long)) extends ConfigBox(_ctx,11L) {
    override def registers = List[ErgoValue[?]](
        ErgoValue.of(Array(
            configIndex,
            profitSharePct
        ).map(java.lang.Long.valueOf),ErgoType.longType()),
        ErgoValue.of(List[(Long,Long)](createDAOFee,createVoteFee,createProposalFee,castVoteFee).map((f: (Long,Long)) => (java.lang.Long.valueOf(f._1),java.lang.Long.valueOf(f._2))).toArray,ErgoType.pairType(ErgoType.longType(),ErgoType.longType()))
    )
}

object PaideiaFeeConfigBox {
    def apply(ctx: BlockchainContextImpl, createDAOFee: (Long,Long), createVoteFee: (Long,Long), createProposalFee: (Long,Long), castVoteFee: (Long,Long), profitSharePct: Long) = {
        val res = new PaideiaFeeConfigBox(ctx,profitSharePct,createDAOFee, createVoteFee, createProposalFee, castVoteFee)
        res.tokens = List(
            new ErgoToken(Env.configTokenId,1L)
        )
        res
    }
}
