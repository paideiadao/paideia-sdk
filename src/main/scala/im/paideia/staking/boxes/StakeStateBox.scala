package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.common.boxes.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.ErgoType
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.OutBox
import special.collection.Coll
import special.collection.CollBuilder
import special.collection.CollOverArray
import scala.collection.JavaConverters._
import scala.collection.mutable.Buffer
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.ContextVar
import im.paideia.staking.contracts.StakeState
import org.ergoplatform.appkit.ErgoToken
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoId
import sigmastate.utils.Helpers
import sigmastate.Values
import im.paideia.staking._
import im.paideia.common.boxes._
import sigmastate.eval.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder
import im.paideia.util.ConfKeys
import scorex.crypto.hash.Blake2b256
import im.paideia.Paideia
import scorex.crypto.authds.ADDigest
import special.sigma.AvlTree
import io.getblok.getblok_plasma.collections.OpResult
import im.paideia.DAO
import im.paideia.governance.VoteRecord
import sigmastate.AvlTreeData

case class StakeStateBox(
  _ctx: BlockchainContextImpl,
  useContract: StakeState,
  state: TotalStakingState,
  _value: Long,
  var extraTokens: List[ErgoToken],
  dao: DAO,
  var stakedTokenTotal: Long,
  var nextEmission: Long,
  var profit: Array[Long],
  var stateDigest: ADDigest,
  var participationDigest: ADDigest,
  var snapshots: Array[StakingSnapshot],
  var voted: Long,
  var votedTotal: Long
) extends PaideiaBox {

  ctx      = _ctx
  value    = _value
  contract = useContract.contract

  override def registers = List[ErgoValue[?]](
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        Array(
          state.currentStakingState.stakeRecords.ergoValue(Some(stateDigest)).getValue,
          state.currentStakingState.participationRecords
            .ergoValue(Some(participationDigest))
            .getValue
        )
      )
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        Array(
          nextEmission,
          state.currentStakingState.totalStaked(Some(stateDigest)),
          state.currentStakingState.stakers(Some(stateDigest)),
          voted,
          votedTotal
        ).++(profit)
      )
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        snapshots.map(kv => kv.totalStaked)
      )
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray[(AvlTree, AvlTree)](
        snapshots
          .map(kv => {
            val snap = state.firstMatchingSnapshot(kv.stakeDigest)
            (
              snap.stakeRecords.ergoValue(Some(kv.stakeDigest)).getValue,
              snap.participationRecords.ergoValue(Some(kv.participationDigest)).getValue
            )
          })
      )
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        snapshots.toArray.map(kv => Colls.fromArray(kv.profit.toArray))
      )
    )
  )

  override def tokens: List[ErgoToken] = {
    List[ErgoToken](
      new ErgoToken(
        dao.config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid),
        1L
      ),
      new ErgoToken(
        dao.config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),
        stakedTokenTotal + 1L
      )
    ) ++ extraTokens
  }

  def stake(
    stakingKey: String,
    amount: Long,
    inPlace: Boolean = false
  ): StakingContextVars = {
    val stakeRecord = StakeRecord(
      amount,
      0L,
      List.fill(
        dao.config
          .getArray[Object](ConfKeys.im_paideia_staking_profit_tokenids)
          .size + 1
      )(0L)
    )

    stakedTokenTotal += amount
    val stakeResult = state.currentStakingState
      .stake(stakingKey, stakeRecord, Left(stateDigest), inPlace)
    stateDigest = stakeResult.digest
    StakingContextVars
      .stake(
        stakingKey,
        stakeRecord,
        stakeResult.toProvenResult
      )
  }

  def addStake(
    stakingKey: String,
    amount: Long
  ): StakingContextVars = {
    val currentStake = state.currentStakingState.getStake(stakingKey, Some(stateDigest))
    val operations = List(
      (
        stakingKey,
        StakeRecord(
          currentStake.stake + amount,
          currentStake.lockedUntil,
          currentStake.rewards
        )
      )
    )
    val result = state.currentStakingState.changeStakes(operations, Left(stateDigest))
    stateDigest = result.digest
    stakedTokenTotal += amount
    StakingContextVars.changeStake(operations, result.toProvenResult)
  }

  def unstake(
    stakingKey: String,
    newStakeRecord: StakeRecord,
    newExtraTokens: List[ErgoToken]
  ): StakingContextVars = {
    val currentStake = state.currentStakingState.getStake(stakingKey, Some(stateDigest))

    stakedTokenTotal -= currentStake.stake - newStakeRecord.stake
    extraTokens = newExtraTokens
    value -= currentStake.rewards(0) - newStakeRecord.rewards(0)
    if (newStakeRecord.stake <= 0L) {
      val stakeProof =
        state.currentStakingState.getStakes(List[String](stakingKey), Some(stateDigest))
      val removeStakeProof =
        state.currentStakingState.unstake(List[String](stakingKey), Left(stateDigest))
      stateDigest = removeStakeProof.digest
      StakingContextVars
        .unstake(stakingKey, stakeProof, removeStakeProof.toProvenResult)
    } else {
      val operations = List((stakingKey, newStakeRecord))
      val result = state.currentStakingState.changeStakes(operations, Left(stateDigest))
      stateDigest = result.digest
      StakingContextVars.changeStake(operations, result.toProvenResult)
    }
  }

  def getStake(
    stakingKey: String,
    history: Int = -1
  ): StakeRecord = {
    history match {
      case -1 => state.currentStakingState.getStake(stakingKey, Some(stateDigest))
      case _ =>
        state
          .snapshots(history)
          .getStake(stakingKey, Some(snapshots(history).stakeDigest))
    }
  }

  def compound(
    batchSize: Int
  ): StakingContextVars = {
    if (snapshots.size < dao.config[Long](ConfKeys.im_paideia_staking_emission_delay))
      throw new Exception("Not enough snapshots gathered yet")
    val snapshot = state.firstMatchingSnapshot(snapshots(0).stakeDigest)
    val keys     = snapshot.getKeys(0, batchSize, Some(snapshots(0).stakeDigest))
    if (keys.size <= 0) throw new Exception("No keys found to compound")
    val currentStakesProvenResult =
      state.currentStakingState.getStakes(keys, Some(stateDigest))
    val snapshotProvenResult = snapshot.getStakes(keys, Some(snapshots(0).stakeDigest))
    val currentStakes = keys.zip(
      currentStakesProvenResult.response.map((p: OpResult[StakeRecord]) =>
        p.tryOp.get.get
      )
    )

    val updatedStakesAndProfits = currentStakes.map((kv: (String, StakeRecord)) => {
      kv._2.stake match {
        case -1 => kv
        case _ => {
          val snapshotStake =
            snapshot.getStake(kv._1, Some(snapshots(0).stakeDigest)).stake

          (
            kv._1,
            StakeRecord(
              kv._2.stake + (BigInt(snapshotStake) * BigInt(
                snapshots(0)
                  .profit(0)
              ) / snapshots(0).totalStaked).toLong,
              kv._2.lockedUntil,
              kv._2.rewards.indices
                .map((i: Int) =>
                  kv._2.rewards(i) + (BigInt(snapshotStake) * BigInt(
                    snapshots(0)
                      .profit(i + 1)
                  ) / snapshots(0).totalStaked).toLong
                )
                .toList
            )
          )

        }
      }
    })

    val result =
      state.currentStakingState.changeStakes(updatedStakesAndProfits, Left(stateDigest))
    stateDigest = result.digest
    val removeProof = snapshot.unstake(keys, Left(snapshots(0).stakeDigest))
    snapshots(0) = StakingSnapshot(
      snapshots(0).totalStaked,
      removeProof.digest,
      snapshots(0).participationDigest,
      snapshots(0).profit
    )
    StakingContextVars
      .compound(
        updatedStakesAndProfits,
        currentStakesProvenResult,
        snapshotProvenResult,
        removeProof.toProvenResult
      )
  }

  def newNextEmission: Long =
    nextEmission + dao.config[Long](ConfKeys.im_paideia_staking_cyclelength)

  def emit(currentTime: Long, tokensInPool: Long): StakingContextVars = {
    if (currentTime < nextEmission) throw new Exception("Not time for new emission yet")
    nextEmission = newNextEmission
    val snapshotProfit = profit.map { p => 0L }
    snapshotProfit(0) = Math.min(
      dao.config[Long](ConfKeys.im_paideia_staking_emission_amount),
      tokensInPool - profit(0)
    )
    snapshots = snapshots.slice(1, snapshots.size) ++ Array(
      StakingSnapshot(
        state.currentStakingState.totalStaked(Some(stateDigest)),
        stateDigest,
        participationDigest,
        snapshotProfit.toList
      )
    )
    snapshots(0)                  = snapshots(0).addProfit(profit)
    state.snapshots(nextEmission) = state.currentStakingState.clone(dao.key, nextEmission)
    profit = Array.fill(
      dao.config.getArray[Object](ConfKeys.im_paideia_staking_profit_tokenids).size + 2
    )(0L)
    StakingContextVars.emit
  }

  def profitShare(
    profitToShare: List[Long],
    newExtraTokens: List[ErgoToken]
  ): StakingContextVars = {
    profit = profitToShare.indices
      .map((i: Int) =>
        if (i >= profit.size) profitToShare(i)
        else profit(i) + profitToShare(i)
      )
      .toArray
    stakedTokenTotal += profitToShare(0)
    value += profitToShare(1)
    extraTokens = newExtraTokens
    StakingContextVars.profitShare
  }

  def vote(
    stakeKey: String,
    proposalExpiration: Long,
    previousVote: Option[VoteRecord],
    newVote: VoteRecord
  ): StakingContextVars = {
    val voteChange = newVote.voteCount - previousVote
      .getOrElse(VoteRecord(Array(0L)))
      .voteCount
    votedTotal += voteChange
    voted = voted + (if (previousVote.isDefined) 0 else 1)
    val getStakeProof =
      state.currentStakingState.getStakes(List(stakeKey), Some(stateDigest))
    val currentStake = getStakeProof.response.head.get
    val getParticipationProof = state.currentStakingState.getParticipations(
      List(stakeKey),
      Some(participationDigest)
    )
    val currentParticipationOpt = getParticipationProof.response.head.tryOp.get
    val currentParticipation =
      if (currentParticipationOpt.isDefined) currentParticipationOpt.get
      else ParticipationRecord(0L, 0L)
    currentStake.lockedUntil =
      if (proposalExpiration > currentStake.lockedUntil) proposalExpiration
      else currentStake.lockedUntil
    currentParticipation.voted += (if (previousVote.isDefined) 0 else 1)
    currentParticipation.votedTotal += voteChange
    val updateStakeProof =
      state.currentStakingState.changeStakes(
        List((stakeKey, currentStake)),
        Left(stateDigest)
      )
    val updateParticipationProof =
      state.currentStakingState.changeParticipations(
        List((stakeKey, currentParticipation)),
        Left(participationDigest)
      )
    stateDigest         = updateStakeProof.digest
    participationDigest = updateParticipationProof.digest
    StakingContextVars.vote(
      List((stakeKey, currentStake)),
      List((stakeKey, currentParticipation)),
      updateStakeProof.toProvenResult,
      updateParticipationProof.toProvenResult
    )
  }
}

object StakeStateBox {

  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): StakeStateBox = {
    val contract = StakeState
      .contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList)
      .asInstanceOf[StakeState]
    val dao        = Paideia.getDAO(contract.contractSignature.daoKey)
    val state      = TotalStakingState(dao.key)
    val longValues = inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]].toArray
    val stateTrees = inp
      .getRegisters()
      .get(0)
      .getValue()
      .asInstanceOf[Coll[AvlTree]]
      .map(_.digest.toArray)
    val snapshotStakedTotals =
      inp.getRegisters().get(2).getValue().asInstanceOf[Coll[Long]].toArray
    val snapshotTrees =
      inp.getRegisters().get(3).getValue().asInstanceOf[Coll[(AvlTree, AvlTree)]].toArray
    val snapshotProfit =
      inp.getRegisters().get(4).getValue().asInstanceOf[Coll[Coll[Long]]].toArray

    StakeStateBox(
      ctx,
      contract,
      state,
      inp.getValue(),
      inp.getTokens().subList(2, inp.getTokens().size()).asScala.toList,
      dao,
      inp.getTokens().get(1).getValue() - 1L,
      longValues(0),
      longValues.slice(5, longValues.size),
      ADDigest @@ stateTrees(0),
      ADDigest @@ stateTrees(1),
      snapshotStakedTotals
        .zip(snapshotTrees)
        .zip(snapshotProfit)
        .map((vals: ((Long, (AvlTree, AvlTree)), Coll[Long])) =>
          StakingSnapshot(
            vals._1._1,
            ADDigest @@ vals._1._2._1.digest.toArray,
            ADDigest @@ vals._1._2._2.digest.toArray,
            vals._2.toArray.toList
          )
        ),
      longValues(3),
      longValues(4)
    )
  }
}
