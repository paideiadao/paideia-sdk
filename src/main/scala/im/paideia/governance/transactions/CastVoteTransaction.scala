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
import sigma.Coll
import org.ergoplatform.appkit.ContextVar
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import sigma.AvlTree
import scorex.crypto.authds.ADDigest
import im.paideia.staking.boxes.StakeStateBox
import scorex.crypto.hash.Blake2b256
import im.paideia.staking.contracts.StakeVote
import im.paideia.governance.VoteRecord
import sigma.Colls
import scorex.util.encode.Base16
import im.paideia.util.TxTypes

final case class CastVoteTransaction(
  _ctx: BlockchainContextImpl,
  proposalIndex: Int,
  stakeKey: String,
  vote: VoteRecord,
  dao: DAO,
  _changeAddress: Address,
  userAddress: Address
) extends PaideiaTransaction {
  ctx           = _ctx
  fee           = 1850000L
  changeAddress = _changeAddress

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
        .asInstanceOf[Coll[Int]](0) == proposalIndex
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
    stakeKey,
    proposalIndex,
    Left(proposalDigest)
  )

  val result = proposalContract
    .castVote(
      ctx,
      proposalInput,
      vote,
      stakeKey,
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

  val stakeVoteContract =
    StakeVote(ConfKeys.im_paideia_contracts_staking_vote, dao.key)

  val stakeVoteInput =
    stakeVoteContract.boxes(stakeVoteContract.getUtxoSet.toArray.apply(0))

  val getProof = TotalStakingState(dao.key).currentStakingState
    .getStakes(List(stakeKey), Some(stakeStateInputBox.stateDigest))

  val stakingContextVars = stakeStateInputBox.vote(
    stakeKey,
    proposalInput.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]](0),
    currentVote,
    vote
  )

  val stakeStateContextVars = stakingContextVars.stakingStateContextVars.::(
    ContextVar.of(
      1.toByte,
      stakeStateInputBox.useContract
        .getConfigContext(Some(configDigest), ConfKeys.im_paideia_contracts_staking_vote)
    )
  )

  val stakeVoteContextVars = stakingContextVars.companionContextVars
    .::(
      ContextVar.of(
        0.toByte,
        stakeVoteContract.getConfigContext(Some(configDigest))
      )
    )

  val proposalContext = List(
    ContextVar.of(0.toByte, TxTypes.VOTE),
    ContextVar.of(
      3.toByte,
      getProof.proof.ergoValue
    ),
    ContextVar.of(10.toByte, ErgoValueBuilder.buildFor(0, 0L)),
    ContextVar.of(
      4.toByte,
      ErgoValueBuilder.buildFor(
        Colls.fromArray(VoteRecord.convertsVoteRecord.convertToBytes(vote))
      )
    ),
    ContextVar.of(
      5.toByte,
      ErgoValueBuilder.buildFor(
        Colls.fromArray(Base16.decode(stakeKey).get)
      )
    )
  ) ++ result._1

  val userOutput = _ctx
    .newTxBuilder()
    .outBoxBuilder()
    .value(1000000L)
    .tokens(
      new ErgoToken(stakeKey, 1L)
    )
    .contract(userAddress.toErgoContract)
    .build()

  inputs = List(
    stakeStateInput.withContextVars(stakeStateContextVars: _*),
    stakeVoteInput.withContextVars(stakeVoteContextVars: _*),
    proposalInput.withContextVars(proposalContext: _*)
  )
  dataInputs = List(configInput)
  outputs = List(
    stakeStateInputBox.outBox,
    stakeVoteContract.box(ctx).outBox,
    result._2.outBox,
    userOutput
  )
}
