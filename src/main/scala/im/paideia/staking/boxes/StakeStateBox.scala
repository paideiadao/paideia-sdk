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
import im.paideia.governance.Proposal
import io.getblok.getblok_plasma.collections.ProvenResult

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
        Array(
          Colls.fromArray(snapshots.map(kv => kv.totalStaked)),
          Colls.fromArray(snapshots.map(kv => kv.voted)),
          Colls.fromArray(snapshots.map(kv => kv.votedTotal)),
          Colls.fromArray(snapshots.map(kv => kv.pureParticipationWeight)),
          Colls.fromArray(snapshots.map(kv => kv.participationWeight))
        )
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
    val snapshotParticipationResult =
      snapshot.getParticipations(keys, Some(snapshots(0).participationDigest))
    val currentStakes = keys
      .zip(
        currentStakesProvenResult.response.map((p: OpResult[StakeRecord]) =>
          p.tryOp.get.get
        )
      )
      .zip(
        snapshotParticipationResult.response.map((p: OpResult[ParticipationRecord]) =>
          p.tryOp.get
        )
      )

    val actualPPWeight: Byte =
      if (snapshots(0).voted > 0)
        snapshots(0).pureParticipationWeight.toByte
      else
        0.toByte

    val actualPWeight: Byte =
      if (snapshots(0).votedTotal > 0)
        snapshots(0).participationWeight.toByte
      else
        0.toByte

    val totalParticipationWeight: Byte =
      (actualPPWeight + actualPWeight).toByte

    val stakingWeight: Byte =
      if (totalParticipationWeight > 0)
        (100.toByte - totalParticipationWeight).max(0.toByte).toByte
      else
        100.toByte

    val totalWeight: Byte = (totalParticipationWeight + stakingWeight).toByte

    def calcReward(
      reward: Long,
      stake: Long,
      participation: Long,
      pureParticipation: Long
    ): Long = {
      ((((BigInt(stake) * BigInt(reward) / snapshots(0).totalStaked) * stakingWeight) +
        (if (actualPPWeight > 0)
           (BigInt(pureParticipation) * BigInt(reward) / snapshots(0).voted) * snapshots(
             0
           ).pureParticipationWeight
         else
           BigInt(0)) +
        (if (actualPWeight > 0)
           (BigInt(participation) * BigInt(reward) / snapshots(0).votedTotal) * snapshots(
             0
           ).participationWeight
         else
           BigInt(0))) /
        totalWeight).toLong
    }

    val updatedStakesAndProfits =
      currentStakes.map((kv: ((String, StakeRecord), Option[ParticipationRecord])) => {
        kv._1._2.stake match {
          case -1 => kv._1
          case _ => {
            val snapshotStake =
              snapshot.getStake(kv._1._1, Some(snapshots(0).stakeDigest)).stake
            val snapshotP  = if (kv._2.isDefined) kv._2.get.votedTotal else 0L
            val snapshotPP = if (kv._2.isDefined) kv._2.get.voted else 0L
            (
              kv._1._1,
              StakeRecord(
                kv._1._2.stake + calcReward(
                  snapshots(0).profit(0),
                  snapshotStake,
                  snapshotP,
                  snapshotPP
                ),
                kv._1._2.lockedUntil,
                kv._1._2.rewards.indices
                  .map((i: Int) =>
                    kv._1._2.rewards(i) + calcReward(
                      snapshots(0).profit(i + 1),
                      snapshotStake,
                      snapshotP,
                      snapshotPP
                    )
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
      snapshots(0).voted,
      snapshots(0).votedTotal,
      removeProof.digest,
      snapshots(0).participationDigest,
      snapshots(0).profit,
      snapshots(0).pureParticipationWeight,
      snapshots(0).participationWeight
    )
    StakingContextVars
      .compound(
        updatedStakesAndProfits,
        currentStakesProvenResult,
        snapshotProvenResult,
        removeProof.toProvenResult,
        snapshotParticipationResult
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
    val participationWeight =
      dao.config
        .withDefault[Byte](ConfKeys.im_paideia_staking_weight_participation, 0.toByte)
        .toLong
    val pureParticipationWeight = dao.config
      .withDefault[Byte](ConfKeys.im_paideia_staking_weight_pureparticipation, 0.toByte)
      .toLong
    snapshots = snapshots.slice(1, snapshots.size) ++ Array(
      StakingSnapshot(
        state.currentStakingState.totalStaked(Some(stateDigest)),
        voted,
        votedTotal,
        stateDigest,
        participationDigest,
        snapshotProfit.toList,
        pureParticipationWeight,
        participationWeight
      )
    )
    voted                         = 0L
    votedTotal                    = 0L
    snapshots(0)                  = snapshots(0).addProfit(profit)
    state.snapshots(nextEmission) = state.currentStakingState.clone(dao.key, nextEmission)
    val currentParticipation =
      state.currentStakingState.participationRecords.getMap(Some(participationDigest)).get
    val participationResult = state.currentStakingState.participationRecords
      .deleteWithDigest(currentParticipation.toMap.keys.toArray: _*)(
        Left(participationDigest)
      )
    participationDigest = participationResult.digest
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
    voteProof: ProvenResult[VoteRecord],
    newVote: VoteRecord
  ): StakingContextVars = {
    val previousVote = voteProof.response.head.tryOp.get
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
      if (currentParticipationOpt.isDefined)
        state.currentStakingState.changeParticipations(
          List((stakeKey, currentParticipation)),
          Left(participationDigest)
        )
      else
        state.currentStakingState.insertParticipations(
          List((stakeKey, currentParticipation)),
          Left(participationDigest)
        )
    stateDigest         = updateStakeProof.digest
    participationDigest = updateParticipationProof.digest
    StakingContextVars.vote(
      voteProof,
      getStakeProof,
      getParticipationProof,
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
    val snapshotValues =
      inp.getRegisters().get(2).getValue().asInstanceOf[Coll[Coll[Long]]].toArray
    val snapshotStakedTotals =
      snapshotValues(0).toArray
    val snapshotVoted       = snapshotValues(1).toArray
    val snapshotVotedTotals = snapshotValues(2).toArray
    val snapshotPPWeight    = snapshotValues(3).toArray
    val snapshotPWeight     = snapshotValues(4).toArray
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
      snapshotStakedTotals.indices
        .map((i: Int) =>
          StakingSnapshot(
            snapshotStakedTotals(i),
            snapshotVoted(i),
            snapshotVotedTotals(i),
            ADDigest @@ snapshotTrees(i)._1.digest.toArray,
            ADDigest @@ snapshotTrees(i)._2.digest.toArray,
            snapshotProfit(i).toArray.toList,
            snapshotPPWeight(i),
            snapshotPWeight(i)
          )
        )
        .toArray,
      longValues(3),
      longValues(4)
    )
  }
}
