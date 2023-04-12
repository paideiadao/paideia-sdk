package im.paideia.governance.boxes

import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.common.boxes.PaideiaBox
import im.paideia.governance.contracts.Vote
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.ErgoId
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoToken
import im.paideia.util.ConfKeys
import sigmastate.eval.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import org.ergoplatform.appkit.InputBox
import scorex.crypto.hash.Blake2b256
import im.paideia.Paideia
import special.collection.Coll

case class VoteBox(
  _ctx: BlockchainContextImpl,
  daoConfig: DAOConfig,
  voteKey: String,
  stakeKey: String,
  releaseTime: Long,
  useContract: Vote
) extends PaideiaBox {
  ctx      = _ctx
  value    = 1000000L
  contract = useContract.contract

  override def registers: List[ErgoValue[_]] = {
    List(
      ErgoValueBuilder.buildFor(releaseTime),
      ErgoValueBuilder.buildFor(Colls.fromArray(ErgoId.create(voteKey).getBytes()))
    )
  }

  override def tokens: List[ErgoToken] = {
    List(
      new ErgoToken(
        new ErgoId(daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_vote_tokenid)),
        1L
      ),
      new ErgoToken(ErgoId.create(stakeKey), 1L)
    )
  }
}

object VoteBox {
  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): VoteBox = {
    val contract = Vote
      .contractInstances(Blake2b256(inp.getErgoTree().bytes).array.toList)
      .asInstanceOf[Vote]
    VoteBox(
      ctx,
      Paideia.getConfig(contract.contractSignature.daoKey),
      new ErgoId(inp.getRegisters().get(1).getValue.asInstanceOf[Coll[Byte]].toArray)
        .toString(),
      inp.getTokens().get(1).getId().toString(),
      inp.getRegisters().get(0).getValue().asInstanceOf[Long],
      contract
    )
  }
}
