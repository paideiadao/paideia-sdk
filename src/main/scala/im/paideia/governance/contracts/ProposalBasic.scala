package im.paideia.governance.contracts

import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContract
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.governance.boxes.ProposalBasicBox
import im.paideia.Paideia
import im.paideia.common.PaideiaEventResponse
import im.paideia.governance.transactions.EvaluateProposalBasicTransaction
import im.paideia.common.PaideiaEvent
import im.paideia.common.BlockEvent
import special.collection.Coll
import org.ergoplatform.appkit.Address
import im.paideia.util.Env
import org.ergoplatform.appkit.InputBox
import im.paideia.util.ConfKeys
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ContextVar
import im.paideia.governance.VoteRecord

class ProposalBasic(contractSignature: PaideiaContractSignature)
  extends PaideiaContract(contractSignature)
  with ProposalContract {

  def box(
    ctx: BlockchainContextImpl,
    proposalIndex: Int,
    voteCount: Array[Long],
    totalVotes: Long,
    endTime: Long,
    passed: Int
  ): ProposalBasicBox = {
    ProposalBasicBox(
      ctx,
      Paideia.getDAO(contractSignature.daoKey),
      Paideia.getConfig(Env.paideiaDaoKey)(
        ConfKeys.im_paideia_fees_createproposal_paideia
      ),
      proposalIndex,
      voteCount,
      totalVotes,
      endTime,
      passed,
      this
    )
  }

  override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    val response: PaideiaEventResponse = event match {
      case be: BlockEvent => {
        PaideiaEventResponse.merge(
          boxes.values
            .map((b: InputBox) => {
              if ((b.getRegisters().get(0).getValue().asInstanceOf[Coll[Int]](1) < 0)
                  && (be.block.getHeader().getTimestamp() > b
                    .getRegisters()
                    .get(1)
                    .getValue()
                    .asInstanceOf[Coll[Long]](0))) {
                PaideiaEventResponse(
                  1,
                  List(
                    EvaluateProposalBasicTransaction(
                      be.ctx,
                      Paideia.getDAO(contractSignature.daoKey),
                      b,
                      Address.create(Env.operatorAddress).getErgoAddress
                    )
                  )
                )
              } else {
                PaideiaEventResponse(0)
              }
            })
            .toList
        )
      }
      case _ => PaideiaEventResponse(0)
    }
    val superResponse = super.handleEvent(event)
    response
  }

  override lazy val constants: HashMap[String, Object] = {
    val cons = new HashMap[String, Object]()
    cons.put(
      "_IM_PAIDEIA_FEES_CREATE_PROPOSAL_PAIDEIA",
      ConfKeys.im_paideia_fees_createproposal_paideia.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_CONTRACTS_SPLIT_PROFIT",
      ConfKeys.im_paideia_contracts_split_profit.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_STAKING_STATE_TOKENID",
      ConfKeys.im_paideia_staking_state_tokenid.ergoValue.getValue()
    )
    cons.put(
      "_IM_PAIDEIA_DAO_QUORUM",
      ConfKeys.im_paideia_dao_quorum.ergoValue.getValue()
    )
    cons.put("_IM_PAIDEIA_DAO_KEY", ErgoId.create(contractSignature.daoKey).getBytes())
    cons.put("_PAIDEIA_DAO_KEY", ErgoId.create(Env.paideiaDaoKey).getBytes())
    cons.put("_PAIDEIA_TOKEN_ID", ErgoId.create(Env.paideiaTokenId).getBytes())
    cons
  }

  def castVote(
    ctx: BlockchainContextImpl,
    inputBox: InputBox,
    vote: VoteRecord,
    voteKey: String
  ): (List[ContextVar], PaideiaBox) = {
    val inp      = ProposalBasicBox.fromInputBox(ctx, inputBox)
    val proposal = Paideia.getDAO(contractSignature.daoKey).proposals(inp.proposalIndex)
    if (!proposal.votes.getTempMap.isDefined) proposal.votes.initiate()
    val voteId      = ErgoId.create(voteKey)
    val lookUp      = proposal.votes.lookUp(voteId)
    val lookUpProof = ContextVar.of(1.toByte, lookUp.proof.ergoValue)
    val currentVote = lookUp.response(0).tryOp.get
    currentVote match {
      case None => {
        val insert = proposal.votes.insert(((voteId, vote)))
        (
          List(
            lookUpProof,
            ContextVar.of(2.toByte, insert.proof.ergoValue)
          ),
          box(ctx, inp.proposalIndex, inp.voteCount.zip(vote.votes).map {
            (ll: (Long, Long)) =>
              ll._1 + ll._2
          }, inp.totalVotes + vote.voteCount, inp.endTime, inp.passed)
        )
      }
      case Some(currentVoteRecord) => {
        val voteChange = vote - currentVoteRecord

        val update = proposal.votes.update((voteId, vote))

        (
          List(
            lookUpProof,
            ContextVar.of(2.toByte, update.proof.ergoValue)
          ),
          box(
            ctx,
            inp.proposalIndex,
            inp.voteCount.zip(voteChange.votes).map { (ll: (Long, Long)) =>
              ll._1 + ll._2
            },
            inp.totalVotes + voteChange.voteCount,
            inp.endTime,
            inp.passed
          )
        )
      }
    }
  }

}

object ProposalBasic extends PaideiaActor {

  override def apply(contractSignature: PaideiaContractSignature): ProposalBasic =
    getContractInstance[ProposalBasic](
      contractSignature,
      new ProposalBasic(contractSignature)
    )
}
