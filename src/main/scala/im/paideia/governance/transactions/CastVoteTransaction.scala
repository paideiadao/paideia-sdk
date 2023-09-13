package im.paideia.governance.transactions

import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.Address
import im.paideia.governance.boxes.CastVoteBox
import org.ergoplatform.sdk.ErgoId
import org.ergoplatform.appkit.InputBox
import im.paideia.DAO
import im.paideia.common.filtering._
import im.paideia.Paideia
import im.paideia.util.ConfKeys
import org.ergoplatform.sdk.ErgoToken
import im.paideia.common.contracts.PaideiaContractSignature
import special.collection.Coll
import org.ergoplatform.appkit.ContextVar
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import special.sigma.AvlTree
import scorex.crypto.authds.ADDigest
import im.paideia.staking.boxes.StakeStateBox
import scorex.crypto.hash.Blake2b256
import im.paideia.staking.contracts.StakeVote

final case class CastVoteTransaction(
  _ctx: BlockchainContextImpl,
  castVoteInput: InputBox,
  dao: DAO,
  _changeAddress: Address
) extends PaideiaTransaction {
  ctx           = _ctx
  fee           = 1850000L
  changeAddress = _changeAddress

  val castVoteBox = CastVoteBox.fromInputBox(ctx, castVoteInput)

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

  val proposalDigest =
    ADDigest @@ proposalInput
      .getRegisters()
      .get(2)
      .getValue()
      .asInstanceOf[AvlTree]
      .digest
      .toArray

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

  val stakeStateInputBox = StakeStateBox.fromInputBox(ctx, stakeStateInput)

  val proposalContract = Paideia
    .getProposalContract(Blake2b256(proposalInput.getErgoTree().bytes).array.toList)

  val currentVote = proposalContract.getVote(
    castVoteBox.stakeKey,
    castVoteBox.proposalIndex,
    Left(proposalDigest)
  )

  val result = proposalContract
    .castVote(
      ctx,
      proposalInput,
      castVoteBox.vote,
      castVoteBox.stakeKey,
      Left(proposalDigest)
    )

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
        ConfKeys.im_paideia_dao_threshold,
        ConfKeys.im_paideia_staking_state_tokenid
      )(Some(configDigest))
    )
  )

  val stakeVoteContract = StakeVote(
    dao
      .config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking_vote)
      .withDaoKey(dao.key)
  )
  val stakeVoteInput =
    stakeVoteContract.boxes(stakeVoteContract.getUtxoSet.toArray.apply(0))

  val getProof = TotalStakingState(dao.key).currentStakingState
    .getStakes(List(castVoteBox.stakeKey), Some(stakeStateInputBox.stateDigest))

  val stakingContextVars = stakeStateInputBox.vote(
    castVoteBox.stakeKey,
    proposalInput.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]](0),
    currentVote,
    castVoteBox.vote
  )

  val stakeStateContextVars = stakingContextVars.stakingStateContextVars.::(
    ContextVar.of(
      0.toByte,
      stakeStateInputBox.useContract.getConfigContext(Some(configDigest))
    )
  )

  val stakeVoteContextVars = stakingContextVars.companionContextVars
    .::(
      ContextVar.of(
        0.toByte,
        stakeVoteContract.getConfigContext(Some(configDigest))
      )
    )

  val extraContext = List(
    ContextVar.of(
      3.toByte,
      getProof.proof.ergoValue
    ),
    ContextVar.of(4.toByte, ErgoValueBuilder.buildFor(0, 0L))
  )

  val userOutput = _ctx
    .newTxBuilder()
    .outBoxBuilder()
    .value(1000000L)
    .tokens(
      new ErgoToken(castVoteBox.stakeKey, 1L)
    )
    .contract(castVoteBox.userAddress.toErgoContract)
    .build()

  inputs = List(
    stakeStateInput.withContextVars(stakeStateContextVars: _*),
    stakeVoteInput.withContextVars(stakeVoteContextVars: _*),
    proposalInput.withContextVars(configContext ++ result._1 ++ extraContext: _*),
    castVoteInput
  )
  dataInputs = List(configInput)
  outputs = List(
    stakeStateInputBox.outBox,
    stakeVoteContract.box(ctx).outBox,
    result._2.outBox,
    userOutput
  )
}
