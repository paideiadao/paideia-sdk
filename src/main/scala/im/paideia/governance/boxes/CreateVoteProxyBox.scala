package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.contracts.CreateVoteProxy
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.InputBox
import scorex.crypto.hash.Blake2b256
import im.paideia.util.Env
import special.collection.Coll
import sigmastate.serialization.ErgoTreeSerializer
import org.ergoplatform.appkit.impl.ErgoTreeContract

case class CreateVoteProxyBox(_ctx: BlockchainContextImpl, stakeKey: String, userAddress: Address, useContract: CreateVoteProxy) extends PaideiaBox
{
    ctx = _ctx
    contract = useContract.contract
    value = 2000000L

    override def registers: List[ErgoValue[_]] = {
        List(
            ErgoValue.of(userAddress.toPropositionBytes())
        )
    }

    override def tokens: List[ErgoToken] = {
        List(
            new ErgoToken(ErgoId.create(stakeKey),1L)
        )
    }
}

object CreateVoteProxyBox {
    def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): CreateVoteProxyBox = {
        val contract = CreateVoteProxy.contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList).asInstanceOf[CreateVoteProxy]
        CreateVoteProxyBox(
            ctx,
            inp.getTokens().get(0).getId().toString(),
            Address.fromPropositionBytes(Env.networkType,inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray),
            contract)
    }
}