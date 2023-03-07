package im.paideia.governance.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.CastVote
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigmastate.eval.Colls
import org.ergoplatform.appkit.InputBox
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import im.paideia.governance.VoteRecord
import org.ergoplatform.appkit.Address

final case class CastVoteBox(
    _ctx: BlockchainContextImpl,
    voteKey: String,
    proposalIndex: Int,
    vote: VoteRecord,
    userAddress: Address,
    useContract: CastVote
) extends PaideiaBox
{
    ctx = _ctx
    contract = useContract.contract
    value = 10000000L

    override def tokens: List[ErgoToken] = {
        List(
            new ErgoToken(voteKey,1L)
        )
    }

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValueBuilder.buildFor(proposalIndex),
            ErgoValueBuilder.buildFor(Colls.fromArray(VoteRecord.convertsVoteRecord.convertToBytes(vote))),
            ErgoValueBuilder.buildFor(Colls.fromArray(userAddress.toPropositionBytes()))
        )
    }
}

object CastVoteBox {
    def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): CastVoteBox = {
        val contract = CastVote.contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList).asInstanceOf[CastVote]
        CastVoteBox(
            ctx,
            inp.getTokens().get(0).getId().toString(),
            inp.getRegisters().get(0).getValue().asInstanceOf[Int],
            VoteRecord.convertsVoteRecord.convertFromBytes(inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Byte]].toArray),
            Address.fromPropositionBytes(ctx.getNetworkType(),inp.getRegisters().get(2).getValue().asInstanceOf[Coll[Byte]].toArray),
            contract)
    }
}