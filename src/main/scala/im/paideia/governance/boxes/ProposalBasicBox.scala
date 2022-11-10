package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAO
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.ErgoType
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.contracts.ProposalBasic
import scorex.crypto.hash.Blake2b256
import special.collection.Coll
import im.paideia.Paideia
import im.paideia.util.Env
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigmastate.eval.Colls

case class ProposalBasicBox(_ctx: BlockchainContextImpl, dao: DAO, paideiaTokens: Long, proposalIndex: Int, voteCount: Array[Long], totalVotes: Long, endTime: Long, passed: Short, useContract: ProposalBasic) extends PaideiaBox
{
    ctx = _ctx
    value = if (passed!=0) 1000000L else 4000000L
    contract = useContract.contract

    override def tokens: List[ErgoToken] = {
        List(
            new ErgoToken(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid), 1L)
        )++(if (passed==0) List(new ErgoToken(Env.paideiaTokenId,paideiaTokens)) else List[ErgoToken]())
    }

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValueBuilder.buildFor(proposalIndex),
            ErgoValueBuilder.buildFor(
                Colls.fromArray(Array[Long](endTime,totalVotes) ++ voteCount)
            ),
            ErgoValueBuilder.buildFor(passed),
            dao.proposals(proposalIndex.toInt).votes.ergoValue
        )
    }
}

object ProposalBasicBox {
    def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ProposalBasicBox = {
        val contract = ProposalBasic.contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList).asInstanceOf[ProposalBasic]
        val longs = inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]].map(_.toLong).toArray
        val voteCount = longs.slice(2,longs.size)
        ProposalBasicBox(ctx,Paideia.getDAO(contract.contractSignature.daoKey),inp.getTokens.get(1).getValue,inp.getRegisters().get(0).getValue().asInstanceOf[Int],voteCount,longs(1),longs(0),inp.getRegisters().get(2).getValue().asInstanceOf[Short],contract)
    }
}
