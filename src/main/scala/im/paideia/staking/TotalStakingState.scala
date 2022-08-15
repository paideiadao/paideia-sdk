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

class TotalStakingState(
    stakingConfig: StakingConfig,
    currentStakingState: StakingState,
    snapshots: Queue[(Long,StakingState)],
    var nextEmission: Long
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

    def compound(batchSize: Int): (Coll[(Coll[Byte],Coll[Byte])],Coll[Byte],Coll[Byte]) = {
        if (this.snapshots.size < this.stakingConfig.emissionDelay) throw new Exception("Not enough snapshots gathered yet")
        val snapshot = this.snapshots.front
        val keys = snapshot._2.getKeys(0,batchSize)
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
        keys.foreach((f: String) => snapshot._2.unstake(f))
        val byteOps = new CollOverArray[(Coll[Byte],Coll[Byte])](updatedStakes.map((kv: (String,Long)) =>
            (new CollOverArray(ByteConversion.convertsId.convertToBytes(ErgoId.create(kv._1))),new CollOverArray(ByteConversion.convertsLongVal.convertToBytes(kv._2)))
        ).toArray)
        (byteOps,new CollOverArray[Byte](currentStakesProvenResult.proof.bytes),new CollOverArray(snapshotProvenResult.proof.bytes))
    }

    def emit(currentTime: Long): Unit = {
        if (currentTime<nextEmission) throw new Exception("Not time for new emission yet")
        if (this.snapshots.size >= this.stakingConfig.emissionDelay)
            if (this.snapshots.front._2.size()>0)
                throw new Exception("Not done compounding")
            else
                this.snapshots.dequeue()
        this.nextEmission += this.stakingConfig.cycleLength
        this.snapshots.enqueue((this.currentStakingState.totalStaked,this.currentStakingState.clone()))
    }

    override def toString(): String = {
        this.currentStakingState.toString() +
        "Snapshots: " + this.snapshots.size.toString + "\n"
    }
}

object TotalStakingState {
    def apply(stakingConfig: StakingConfig, nextEmission: Long): TotalStakingState = {
        new TotalStakingState(stakingConfig,StakingState(stakingConfig),new Queue[(Long,StakingState)](),nextEmission: Long)
    }
}
