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
import im.paideia.staking.contracts.PlasmaStaking
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

case class StakeStateBox(
  _ctx: BlockchainContextImpl,
  useContract: PlasmaStaking,
  state: TotalStakingState,
  _value: Long,
  var extraTokens: List[ErgoToken],
  dao: DAO,
  var stakedTokenTotal: Long,
  var nextEmission: Long,
  var profit: Array[Long],
  var stateDigest: ADDigest,
  var snapshots: Array[StakeSnapshot]
) extends PaideiaBox {

  ctx      = _ctx
  value    = _value
  contract = useContract.contract

  override def registers = List[ErgoValue[?]](
    state.currentStakingState.plasmaMap.ergoValue(Some(stateDigest)),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        Array(
          nextEmission,
          state.currentStakingState.size(Some(stateDigest)).toLong,
          state.currentStakingState.totalStaked(Some(stateDigest))
        ).++(profit)
      )
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        snapshots.map(kv => kv.totalStaked)
      )
    ),
    ErgoValueBuilder.buildFor(
      Colls.fromArray(
        snapshots
          .map(kv =>
            state
              .firstMatchingSnapshot(kv.digest)
              .plasmaMap
              .ergoValue(Some(kv.digest))
              .getValue()
          )
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
  ): List[ContextVar] = {
    val stakeRecord = StakeRecord(
      amount,
      List.fill(
        dao.config
          .getArray[Array[Byte]](ConfKeys.im_paideia_staking_profit_tokenids)
          .size + 1
      )(0L)
    )
    stakedTokenTotal += amount
    val result = state.currentStakingState
      .stake(stakingKey, stakeRecord, Left(stateDigest), inPlace)
    stateDigest = result.digest
    StakingContextVars
      .stake(
        stakingKey,
        stakeRecord,
        result.toProvenResult
      )
      .contextVars
  }

  def addStake(
    stakingKey: String,
    amount: Long
  ): List[ContextVar] = {
    val currentStake = state.currentStakingState.getStake(stakingKey, Some(stateDigest))
    val operations = List(
      (stakingKey, StakeRecord(currentStake.stake + amount, currentStake.rewards))
    )
    val result = state.currentStakingState.changeStakes(operations, Left(stateDigest))
    stateDigest = result.digest
    stakedTokenTotal += amount
    StakingContextVars.changeStake(operations, result.toProvenResult).contextVars
  }

  def unstake(
    stakingKey: String,
    newStakeRecord: StakeRecord,
    newExtraTokens: List[ErgoToken]
  ): List[ContextVar] = {
    val currentStake = state.currentStakingState.getStake(stakingKey, Some(stateDigest))
    stakedTokenTotal -= currentStake.stake - newStakeRecord.stake
    extraTokens = newExtraTokens
    value -= currentStake.rewards(0) - newStakeRecord.rewards(0)
    if (newStakeRecord.stake <= 0L) {
      val proof =
        state.currentStakingState.getStakes(List[String](stakingKey), Some(stateDigest))
      val removeProof =
        state.currentStakingState.unstake(List[String](stakingKey), Left(stateDigest))
      stateDigest = removeProof.digest
      StakingContextVars
        .unstake(stakingKey, proof, removeProof.toProvenResult)
        .contextVars
    } else {
      val operations = List((stakingKey, newStakeRecord))
      val result     = state.currentStakingState.changeStakes(operations, Left(stateDigest))
      stateDigest = result.digest
      StakingContextVars.changeStake(operations, result.toProvenResult).contextVars
    }
  }

  def getStake(
    stakingKey: String,
    history: Int = -1
  ): StakeRecord = {
    history match {
      case -1 => state.currentStakingState.getStake(stakingKey, Some(stateDigest))
      case _ =>
        state.snapshots(history).getStake(stakingKey, Some(snapshots(history).digest))
    }
  }

  def compound(
    batchSize: Int
  ): List[ContextVar] = {
    if (snapshots.size < dao.config[Long](ConfKeys.im_paideia_staking_emission_delay))
      throw new Exception("Not enough snapshots gathered yet")
    val snapshot = state.firstMatchingSnapshot(snapshots(0).digest)
    val keys     = snapshot.getKeys(0, batchSize, Some(snapshots(0).digest))
    if (keys.size <= 0) throw new Exception("No keys found to compound")
    val currentStakesProvenResult =
      state.currentStakingState.getStakes(keys, Some(stateDigest))
    val snapshotProvenResult = snapshot.getStakes(keys, Some(snapshots(0).digest))
    val currentStakes = keys.zip(
      currentStakesProvenResult.response.map((p: OpResult[StakeRecord]) => p.tryOp.get.get
      )
    )
    val updatedStakes = currentStakes.map((kv: (String, StakeRecord)) => {
      kv._2.stake match {
        case -1 => kv
        case _ => {
          val snapshotStake = snapshot.getStake(kv._1, Some(snapshots(0).digest)).stake
          (
            kv._1,
            StakeRecord(
              kv._2.stake + (snapshotStake * snapshots(0)
                .profit(0) / snapshots(0).totalStaked),
              kv._2.rewards.indices
                .map((i: Int) =>
                  kv._2.rewards(i) + (snapshotStake * snapshots(0)
                    .profit(i + 1) / snapshots(0).totalStaked)
                )
                .toList
            )
          )
        }
      }
    })
    val result = state.currentStakingState.changeStakes(updatedStakes, Left(stateDigest))
    stateDigest = result.digest
    val removeProof = snapshot.unstake(keys, Left(snapshots(0).digest))
    snapshots(0) =
      StakeSnapshot(snapshots(0).totalStaked, removeProof.digest, snapshots(0).profit)
    StakingContextVars
      .compound(
        updatedStakes,
        currentStakesProvenResult,
        snapshotProvenResult,
        removeProof.toProvenResult
      )
      .contextVars
  }

  def newNextEmission: Long =
    nextEmission + dao.config[Long](ConfKeys.im_paideia_staking_cyclelength)

  def emit(currentTime: Long, tokensInPool: Long): List[ContextVar] = {
    if (currentTime < nextEmission) throw new Exception("Not time for new emission yet")
    nextEmission = newNextEmission
    profit(0) += Math.min(
      dao.config[Long](ConfKeys.im_paideia_staking_emission_amount),
      tokensInPool - profit(0)
    )
    snapshots =
      snapshots.slice(1, snapshots.size) ++ Array(
        StakeSnapshot(
          state.currentStakingState.totalStaked(Some(stateDigest)),
          stateDigest,
          profit.toList
        )
      )
    state.snapshots(nextEmission) = state.currentStakingState.clone(dao.key, nextEmission)
    profit = Array.fill(
      dao.config.getArray[Object](ConfKeys.im_paideia_staking_profit_tokenids).size + 2
    )(0L)
    StakingContextVars.emit.contextVars
  }

  def profitShare(
    profitToShare: List[Long],
    newExtraTokens: List[ErgoToken]
  ): List[ContextVar] = {
    profit = profitToShare.indices
      .map((i: Int) =>
        if (i >= profit.size) profitToShare(i)
        else profit(i) + profitToShare(i)
      )
      .toArray
    stakedTokenTotal += profitToShare(0)
    value += profitToShare(1)
    extraTokens = newExtraTokens
    StakingContextVars.profitShare.contextVars
  }
}

object StakeStateBox {

  def fromInputBox(ctx: BlockchainContextImpl, inp: InputBox): StakeStateBox = {
    val contract = PlasmaStaking
      .contractInstances(Blake2b256(inp.getErgoTree.bytes).array.toList)
      .asInstanceOf[PlasmaStaking]
    val dao        = Paideia.getDAO(contract.contractSignature.daoKey)
    val state      = TotalStakingState(dao.key)
    val longValues = inp.getRegisters().get(1).getValue().asInstanceOf[Coll[Long]].toArray
    val stateTree  = inp.getRegisters().get(0).getValue().asInstanceOf[AvlTree]
    val snapshotStakedTotals =
      inp.getRegisters().get(2).getValue().asInstanceOf[Coll[Long]].toArray
    val snapshotTrees =
      inp.getRegisters().get(3).getValue().asInstanceOf[Coll[AvlTree]].toArray
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
      longValues.slice(3, longValues.size),
      ADDigest @@ stateTree.digest.toArray,
      snapshotStakedTotals
        .zip(snapshotTrees)
        .zip(snapshotProfit)
        .map((vals: ((Long, AvlTree), Coll[Long])) =>
          StakeSnapshot(
            vals._1._1,
            ADDigest @@ vals._1._2.digest.toArray,
            vals._2.toArray.toList
          )
        )
    )
  }
}
