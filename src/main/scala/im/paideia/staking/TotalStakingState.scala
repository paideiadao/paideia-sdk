package im.paideia.staking

import scala.collection.mutable.Queue
import io.getblok.getblok_plasma.collections.ProvenResult
import io.getblok.getblok_plasma.collections.OpResult
import scala.util.Failure
import scala.util.Success

class TotalStakingState(
    stakingConfig: StakingConfig,
    currentStakingState: StakingState,
    snapshots: Queue[(Long,StakingState)]
) {

    def stake(stakingKey: String, amount: Long): ProvenResult[Long] = this.currentStakingState.stake(stakingKey,amount)

    def addStake(stakingKey: String, amount: Long): ProvenResult[Long] = {
        val currentStakeAmount = this.currentStakingState.getStake(stakingKey)
        this.currentStakingState.changeStakes(List((stakingKey, currentStakeAmount+amount)))
    }

    def unstake(stakingKey: String, amount: Long): ProvenResult[Long] = {
        val currentStakeAmount = this.currentStakingState.getStake(stakingKey)
        if (currentStakeAmount <= amount) {
            this.currentStakingState.unstake(stakingKey)
        } else {
            this.currentStakingState.changeStakes(List((stakingKey, currentStakeAmount-amount)))
        }
    }

    def getStake(stakingKey: String, history: Int = -1): Long = {
        history match {
            case -1 => this.currentStakingState.getStake(stakingKey)
            case _ => this.snapshots(history)._2.getStake(stakingKey)
        }
    }

    def compound(batchSize: Int): Unit = {
        val snapshot = this.snapshots.apply(this.stakingConfig.emissionDelay.toInt)
        val keys = snapshot._2.getKeys(0,batchSize)
        val snapshotTotalStaked = snapshot._1
        val currentStakesProvenResult = this.currentStakingState.getStakes(keys)
        val currentStakes = keys.zip(currentStakesProvenResult.response.map((p: OpResult[Long]) => p.tryOp.getOrElse(Some(-1L)).get))
        val updatedStakes = currentStakes.filter((kv: (String,Long)) => kv._2>0).map((kv: (String,Long)) => {
            val snapshotStake = snapshot._2.getStake(kv._1)
            (kv._1,kv._2+(snapshotStake*this.stakingConfig.emissionAmount/snapshotTotalStaked))
        })
        val changeProof = this.currentStakingState.changeStakes(updatedStakes)
        Unit
    }
}

object TotalStakingState {
    def apply(stakingConfig: StakingConfig): TotalStakingState = {
        new TotalStakingState(stakingConfig,StakingState(stakingConfig),new Queue[(Long,StakingState)]())
    }
}
