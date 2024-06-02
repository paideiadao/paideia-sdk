package im.paideia.governance.boxes

import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAO
import org.ergoplatform.sdk.ErgoToken
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
import scorex.crypto.authds.ADDigest
import java.nio.charset.StandardCharsets
import scorex.crypto.authds.legacy.avltree.AVLTree
import special.sigma.AvlTree

case class ProposalBasicBox(
  _ctx: BlockchainContextImpl,
  dao: DAO,
  name: String,
  paideiaTokens: Long,
  proposalIndex: Int,
  voteCount: Array[Long],
  totalVotes: Long,
  endTime: Long,
  passed: Int,
  useContract: ProposalBasic,
  digestOpt: Option[ADDigest] = None
) extends PaideiaBox {
  ctx      = _ctx
  value    = if (passed != -1) 1000000L else 4000000L
  contract = useContract.contract

  override def tokens: List[ErgoToken] = {
    List(
      new ErgoToken(
        dao.config.getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid),
        1L
      )
    ) ++ (if (passed == -1) List(new ErgoToken(Env.paideiaTokenId, paideiaTokens))
          else List[ErgoToken]())
  }

  override def registers: List[ErgoValue[_]] = {
    List(
      ErgoValueBuilder.buildFor(
        Colls.fromArray(Array(proposalIndex, passed))
      ),
      ErgoValueBuilder.buildFor(
        Colls.fromArray(Array[Long](endTime, totalVotes) ++ voteCount)
      ),
      dao.proposals(proposalIndex.toInt).votes.ergoValue(digestOpt),
      ErgoValueBuilder.buildFor(Colls.fromArray(name.getBytes(StandardCharsets.UTF_8)))
    )
  }
}

object ProposalBasicBox {

  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): ProposalBasicBox = {
    val contract = ProposalBasic
      .contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList)
      .asInstanceOf[ProposalBasic]
    val longs =
      inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]].map(_.toLong).toArray
    val voteCount = longs.slice(2, longs.size)
    val ints =
      inp.getRegisters().get(0).getValue().asInstanceOf[Coll[Int]].map(_.toInt).toArray
    val paideiaTokens =
      if (inp.getTokens().size() > 1) inp.getTokens.get(1).getValue else 0L
    val name = new String(
      inp.getRegisters().get(3).getValue().asInstanceOf[Coll[Byte]].toArray,
      StandardCharsets.UTF_8
    )
    val votesTree = inp.getRegisters().get(2).getValue().asInstanceOf[AvlTree]
    ProposalBasicBox(
      ctx,
      Paideia.getDAO(contract.contractSignature.daoKey),
      name,
      paideiaTokens,
      ints(0),
      voteCount,
      longs(1),
      longs(0),
      ints(1),
      contract,
      Some(ADDigest @@ votesTree.digest.toArray)
    )
  }
}
