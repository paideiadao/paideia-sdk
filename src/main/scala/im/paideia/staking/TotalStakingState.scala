package im.paideia.staking

import scala.collection.mutable.Queue
import io.getblok.getblok_plasma.collections.ProvenResult
import io.getblok.getblok_plasma.collections.OpResult
import io.getblok.getblok_plasma.ByteConversion
import scala.util.Failure
import scala.util.Success
import special.collection.Coll
import special.collection.CollOverArrayBuilder
import org.ergoplatform.appkit.ErgoId
import special.collection.PairOfCols
import special.collection.CollOverArray
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.ErgoType
import im.paideia.DAOConfig
import im.paideia.Paideia
import scala.collection.mutable.HashMap
import im.paideia.util.ConfKeys

class TotalStakingState(
    val daoConfig: DAOConfig,
    val currentStakingState: StakingState,
    val snapshots: Queue[(Long,StakingState,List[Long])],
    var profit: Array[Long],
    var nextEmission: Long
) {

    def stake(stakingKey: String, amount: Long): List[ContextVar] = {
        val stakeRecord = StakeRecord(amount,List.fill(daoConfig.getArray[Array[Byte]](ConfKeys.im_paideia_staking_profit_tokenids).size+1)(0L))
        StakingContextVars.stake(stakingKey,stakeRecord,currentStakingState.stake(stakingKey,stakeRecord)).contextVars
    }

    def addStake(stakingKey: String, amount: Long): List[ContextVar] = {
        val currentStake = this.currentStakingState.getStake(stakingKey)
        val operations = List((stakingKey, StakeRecord(currentStake.stake+amount,currentStake.rewards)))
        val result = this.currentStakingState.changeStakes(operations)
        StakingContextVars.changeStake(operations,result).contextVars
    }

    def unstake(stakingKey: String, amount: Long): List[ContextVar] = {
        val currentStake = this.currentStakingState.getStake(stakingKey)
        if (currentStake.stake <= amount) {
            val proof = currentStakingState.getStakes(List[String](stakingKey))
            val removeProof = this.currentStakingState.unstake(List[String](stakingKey))
            StakingContextVars.unstake(stakingKey,proof,removeProof).contextVars
        } else {
            val operations = List((stakingKey, StakeRecord(currentStake.stake-amount,currentStake.rewards)))
            val result = this.currentStakingState.changeStakes(operations)
            StakingContextVars.changeStake(operations,result).contextVars
        }
    }

    def getStake(stakingKey: String, history: Int = -1): StakeRecord = {
        history match {
            case -1 => this.currentStakingState.getStake(stakingKey)
            case _ => this.snapshots(history)._2.getStake(stakingKey)
        }
    }

    def compound(batchSize: Int): List[ContextVar] = {
        if (this.snapshots.size < daoConfig[Int]("im.paideia.staking.emissionDelay")) throw new Exception("Not enough snapshots gathered yet")
        val snapshot = this.snapshots.front
        val keys = snapshot._2.getKeys(0,batchSize)
        if (keys.size <= 0) throw new Exception("No keys found to compound")
        val snapshotTotalStaked = snapshot._1
        val currentStakesProvenResult = this.currentStakingState.getStakes(keys)
        val snapshotProvenResult = snapshot._2.getStakes(keys)
        val currentStakes = keys.zip(currentStakesProvenResult.response.map((p: OpResult[StakeRecord]) => p.tryOp.get.get))
        val updatedStakes = currentStakes.map((kv: (String,StakeRecord)) => {
            kv._2.stake match {
                case -1 => kv
                case _ => {
                    val snapshotStake = snapshot._2.getStake(kv._1).stake
                    (kv._1,StakeRecord(kv._2.stake+(snapshotStake*snapshot._3(0)/snapshotTotalStaked),
                    kv._2.rewards.indices.map((i: Int) =>
                        kv._2.rewards(i)+(snapshotStake*snapshot._3(i+1)/snapshotTotalStaked)).toList))
                }
            }
        })
        this.currentStakingState.changeStakes(updatedStakes)
        val removeProof = snapshot._2.unstake(keys)
        StakingContextVars.compound(updatedStakes,currentStakesProvenResult,snapshotProvenResult,removeProof).contextVars
    }

    def emit(currentTime: Long, tokensInPool: Long): List[ContextVar] = {
        if (currentTime<nextEmission) throw new Exception("Not time for new emission yet")
        if (snapshots.size >= daoConfig[Long](ConfKeys.im_paideia_staking_emission_delay))
            if (snapshots.front._2.size()>0)
                throw new Exception("Not done compounding")
            else
                snapshots.dequeue()
        nextEmission += daoConfig[Long](ConfKeys.im_paideia_staking_cyclelength)
        profit(0) += Math.min(daoConfig[Long](ConfKeys.im_paideia_staking_emission_amount),tokensInPool-profit(0))
        this.snapshots.enqueue((this.currentStakingState.totalStaked,this.currentStakingState.clone(),List(profit:_*)))
        profit = Array.fill(daoConfig.getArray[Array[Byte]](ConfKeys.im_paideia_staking_profit_tokenids).size+2)(0L)
        StakingContextVars.emit.contextVars
    }

    def profitShare(profitToShare: List[Long]): List[ContextVar] = {
        profit.indices.map((i: Int) => profit(i) += profitToShare(i))
        StakingContextVars.profitShare.contextVars
    }

    override def toString(): String = {
        this.currentStakingState.toString() +
        "Snapshots: " + this.snapshots.size.toString + "\n"
    }
}

object TotalStakingState {
    val _stakingStates: HashMap[String,TotalStakingState] = HashMap[String,TotalStakingState]()

    def apply(daoKey: String, nextEmission: Long): TotalStakingState = {
            /* daoConfig("im.paideia.staking.nftId"),
            daoConfig("im.paideia.staking.stakedTokenId"),
            daoConfig("im.paideia.staking.emissionAmount"),
            daoConfig("im.paideia.staking.emissionDelay"),
            daoConfig("im.paideia.staking.cycleLength"),
            daoConfig("im.paideia.staking.profitTokens") */
        val daoConfig = Paideia.getConfig(daoKey)
        val currentState = StakingState()
        val profitTokensSize = daoConfig.getArray[Array[Byte]](ConfKeys.im_paideia_staking_profit_tokenids).size
        val snapshots = Queue[(Long,StakingState,List[Long])](
            Range(0,daoConfig[Long](ConfKeys.im_paideia_staking_emission_delay).toInt).map((_) => (0L,currentState.clone(),List.fill(profitTokensSize+2)(0L))): _*
        )
        val newState = new TotalStakingState(daoConfig,currentState,snapshots,Array.fill(profitTokensSize+2)(0L),nextEmission: Long)
        _stakingStates.put(daoKey,newState)
        newState
    }

    def apply(daoKey: String): TotalStakingState = _stakingStates(daoKey)
}
