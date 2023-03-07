package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.Env
import im.paideia.Paideia
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoValue
import im.paideia.governance.contracts.CreateProposal
import im.paideia.DAO
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigmastate.eval.Colls
import special.sigma.Box
import scorex.crypto.hash.Blake2b256
import org.ergoplatform.appkit.InputBox
import special.collection.Coll

final case class CreateProposalBox(
    _ctx: BlockchainContextImpl,
    proposalBox: Box,
    actionBoxes: Array[Box],
    voteKey: String,
    userAddress: Address,
    useContract: CreateProposal
) extends PaideiaBox
{
    ctx = _ctx
    contract = useContract.contract
    value = 2000000L + proposalBox.value + actionBoxes.foldLeft(0L){(z: Long, b: Box) => z+b.value}

    override def tokens: List[ErgoToken] = List(
        new ErgoToken(voteKey, 1L),
        new ErgoToken(Env.paideiaTokenId, Paideia.getConfig(Env.paideiaDaoKey)(ConfKeys.im_paideia_fees_createproposal_paideia))
    )

    override def registers: List[ErgoValue[_]] = List(
        ErgoValueBuilder.buildFor(Colls.fromArray(userAddress.toPropositionBytes())),
        ErgoValueBuilder.buildFor(Colls.fromArray(
            Array(proposalBox)++actionBoxes
        ))
    )
}

object CreateProposalBox
{
    def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): CreateProposalBox = {
        val contract = CreateProposal.contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList).asInstanceOf[CreateProposal]
        val userAddress = Address.fromPropositionBytes(ctx.getNetworkType(),inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray)
        val boxes = inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Box]]
        val voteKey = inp.getTokens().get(0).getId().toString()
        CreateProposalBox(
            ctx,
            boxes(0),
            boxes.slice(1,boxes.size).toArray,
            voteKey,
            userAddress,
            contract
        )
    }
}
