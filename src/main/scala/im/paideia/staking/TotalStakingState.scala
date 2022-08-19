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

class TotalStakingState(
    val stakingConfig: StakingConfig,
    val currentStakingState: StakingState,
    val snapshots: Queue[(Long,StakingState)],
    var nextEmission: Long
) {

    def stake(stakingKey: String, amount: Long): List[ContextVar] = StakingContextVars.stake(stakingKey,amount,currentStakingState.stake(stakingKey,amount)).contextVars

    def addStake(stakingKey: String, amount: Long): List[ContextVar] = {
        val currentStakeAmount = this.currentStakingState.getStake(stakingKey)
        val operations = List((stakingKey, currentStakeAmount+amount))
        val result = this.currentStakingState.changeStakes(operations)
        StakingContextVars.changeStake(operations,result).contextVars
    }

    def unstake(stakingKey: String, amount: Long): List[ContextVar] = {
        val currentStakeAmount = this.currentStakingState.getStake(stakingKey)
        if (currentStakeAmount <= amount) {
            val result = this.currentStakingState.unstake(List[String](stakingKey))
            StakingContextVars.unstake(stakingKey,result).contextVars
        } else {
            val operations = List((stakingKey, currentStakeAmount-amount))
            val result = this.currentStakingState.changeStakes(operations)
            StakingContextVars.changeStake(operations,result).contextVars
        }
    }

    def getStake(stakingKey: String, history: Int = -1): Long = {
        history match {
            case -1 => this.currentStakingState.getStake(stakingKey)
            case _ => this.snapshots(history)._2.getStake(stakingKey)
        }
    }

    def compound(batchSize: Int): List[ContextVar] = {
        if (this.snapshots.size < this.stakingConfig.emissionDelay) throw new Exception("Not enough snapshots gathered yet")
        val snapshot = this.snapshots.front
        val keys = snapshot._2.getKeys(0,batchSize)
        if (keys.size <= 0) throw new Exception("No keys found to compound")
        val snapshotTotalStaked = snapshot._1
        val currentStakesProvenResult = this.currentStakingState.getStakes(keys)
        val snapshotProvenResult = snapshot._2.getStakes(keys)
        val currentStakes = keys.zip(currentStakesProvenResult.response.map((p: OpResult[Long]) => p.tryOp.getOrElse(Some(-1L)).getOrElse(-1L)))
        val updatedStakes = currentStakes.map((kv: (String,Long)) => {
            kv._2 match {
                case -1 => kv
                case _ => {
                    val snapshotStake = snapshot._2.getStake(kv._1)
                    (kv._1,kv._2+(snapshotStake*this.stakingConfig.emissionAmount/snapshotTotalStaked))
                }
            }
        })
        this.currentStakingState.changeStakes(updatedStakes)
        val removeProof = snapshot._2.unstake(keys)
        StakingContextVars.compound(updatedStakes,currentStakesProvenResult,snapshotProvenResult,removeProof).contextVars
    }

    def emit(currentTime: Long): List[ContextVar] = {
        if (currentTime<nextEmission) throw new Exception("Not time for new emission yet")
        if (this.snapshots.size >= this.stakingConfig.emissionDelay)
            if (this.snapshots.front._2.size()>0)
                throw new Exception("Not done compounding")
            else
                this.snapshots.dequeue()
        this.nextEmission += this.stakingConfig.cycleLength
        this.snapshots.enqueue((this.currentStakingState.totalStaked,this.currentStakingState.clone()))
        StakingContextVars.emit.contextVars
    }

    override def toString(): String = {
        this.currentStakingState.toString() +
        "Snapshots: " + this.snapshots.size.toString + "\n"
    }
}

object TotalStakingState {
    def apply(stakingConfig: StakingConfig, nextEmission: Long): TotalStakingState = {
        val currentState = StakingState(stakingConfig)
        val snapshots = Queue[(Long,StakingState)](
            Range(0,stakingConfig.emissionDelay.toInt).map((_) => (0L,currentState.clone())): _*
        )
        new TotalStakingState(stakingConfig,currentState,snapshots,nextEmission: Long)
    }
}
