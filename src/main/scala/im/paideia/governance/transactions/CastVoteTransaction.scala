package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.Address
import im.paideia.governance.boxes.CastVoteBox
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.InputBox
import im.paideia.DAO
import im.paideia.common.filtering._
import im.paideia.Paideia
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoToken
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.governance.boxes.VoteBox
import im.paideia.governance.contracts.Vote
import special.collection.Coll
import org.ergoplatform.appkit.ContextVar
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import special.sigma.AvlTree
import scorex.crypto.authds.ADDigest

final case class CastVoteTransaction(
  _ctx: BlockchainContextImpl,
  castVoteInput: InputBox,
  dao: DAO,
  _changeAddress: Address
) extends PaideiaTransaction {
  ctx           = _ctx
  fee           = 1000000L
  changeAddress = _changeAddress.getErgoAddress()

  val castVoteBox = CastVoteBox.fromInputBox(ctx, castVoteInput)

  val voteInput = Paideia.getBox(
    new FilterNode(
      FilterType.FTALL,
      List(
        new FilterLeaf(
          FilterType.FTEQ,
          new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_vote_tokenid))
            .toString(),
          CompareField.ASSET,
          0
        ),
        new FilterLeaf(
          FilterType.FTEQ,
          ErgoId.create(castVoteBox.voteKey).getBytes().toIterable,
          CompareField.REGISTER,
          1
        )
      )
    )
  )(0)

  val voteBox: VoteBox = VoteBox.fromInputBox(ctx, voteInput)

  val proposalInput = Paideia
    .getBox(
      new FilterLeaf(
        FilterType.FTEQ,
        new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_dao_proposal_tokenid))
          .toString(),
        CompareField.ASSET,
        0
      )
    )
    .filter((box: InputBox) =>
      box
        .getRegisters()
        .get(0)
        .getValue()
        .asInstanceOf[Coll[Int]](0) == castVoteBox.proposalIndex
    )(0)

  val configInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      dao.key,
      CompareField.ASSET,
      0
    )
  )(0)

  val stakeStateInput = Paideia.getBox(
    new FilterLeaf[String](
      FilterType.FTEQ,
      new ErgoId(dao.config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid))
        .toString(),
      CompareField.ASSET,
      0
    )
  )(0)

  val result = Paideia
    .getProposalContract(proposalInput)
    .castVote(ctx, proposalInput, castVoteBox.vote, castVoteBox.voteKey)

  val compareDigest = dao.config._config.digest

  val configDigest =
    ADDigest @@ configInput
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

  val configContext = List(
    ContextVar.of(
      0.toByte,
      dao.config.getProof(
        ConfKeys.im_paideia_dao_quorum,
        ConfKeys.im_paideia_staking_state_tokenid
      )(Some(configDigest))
    )
  )

  val extraContext = List(
    ContextVar.of(
      3.toByte,
      TotalStakingState(dao.key).currentStakingState
        .getStakes(List(voteBox.stakeKey))
        .proof
        .ergoValue
    ),
    ContextVar.of(4.toByte, ErgoValueBuilder.buildFor(0, 0L))
  )

  val paiActors = Paideia._actorList
  val paiDaos   = Paideia._daoMap

  val voteOutput = voteBox.useContract.box(
    ctx,
    voteBox.voteKey,
    voteBox.stakeKey,
    voteBox.releaseTime.max(
      proposalInput.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]](0)
    )
  )

  val userOutput = _ctx
    .newTxBuilder()
    .outBoxBuilder()
    .tokens(
      new ErgoToken(castVoteBox.voteKey, 1L)
    )
    .contract(castVoteBox.userAddress.toErgoContract)
    .build()

  inputs = List(
    proposalInput.withContextVars(configContext ++ result._1 ++ extraContext: _*),
    voteInput,
    castVoteInput
  )
  dataInputs = List(configInput, stakeStateInput)
  outputs    = List(result._2.outBox, voteOutput.outBox, userOutput)
}
