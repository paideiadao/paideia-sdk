package im.paideia.staking

import io.getblok.getblok_plasma.{PlasmaParameters}
import io.getblok.getblok_plasma.collections.PlasmaMap
import scala.collection.mutable.SortedSet
import sigmastate.AvlTreeFlags
import org.ergoplatform.appkit.ErgoId
import io.getblok.getblok_plasma.ByteConversion.convertsLongVal
import io.getblok.getblok_plasma.collections.ProvenResult
import scala.util.Failure
import scala.util.Success

class StakingState(
    stakingConfig: StakingConfig,
    plasmaParameters: PlasmaParameters,
    plasmaMap: PlasmaMap[ErgoId,Long],
    var totalStaked: Long,
    sortedKeys: SortedSet[String]
) {

    def stake(stakingKey: String, stakeAmount: Long): ProvenResult[Long] = {
        this.totalStaked += stakeAmount
        this.sortedKeys.add(stakingKey) match {
            case true => this.plasmaMap.insert((ErgoId.create(stakingKey),stakeAmount))
            case false => throw new RuntimeException
        }
    }

    def unstake(stakingKey: String): ProvenResult[Long] = {
        this.totalStaked -= this.getStake(stakingKey)
        this.sortedKeys.remove(stakingKey) match {
            case true => this.plasmaMap.delete(ErgoId.create(stakingKey))
            case false => throw new RuntimeException
        }
    }

    def getStakes(stakingKeys: List[String]): ProvenResult[Long] =
        this.plasmaMap.lookUp(stakingKeys.map(key => ErgoId.create(key)): _*)

    def getStake(stakingKey: String): Long =
        this.getStakes(List[String](stakingKey)).response(0).tryOp match {
            case Failure(exception) => throw exception
            case Success(value) => value.get
        }
    
    def changeStakes(newStakes: List[(String,Long)]): ProvenResult[Long] = {
        val currentStakes = this.getStakes(newStakes.map(kv => kv._1))
        this.totalStaked = this.totalStaked -
            currentStakes.response.foldLeft(0L)((x,y) => x+y.tryOp.get.getOrElse(0L)) +
            newStakes.foldLeft(0L)((x,kv) => x+kv._2)
        this.plasmaMap.update(newStakes.map(kv => (ErgoId.create(kv._1),kv._2)): _*)
    }

    def getKeys(from: Int = 0, n: Int = 1): List[String] =
        this.sortedKeys.toList.slice(from,from+n)

    def size(): Int = this.sortedKeys.size

    override def toString(): String = {
        "State:\n" +
        "Number of stakers: " + this.size.toString + "\n" +
        "Total staked: " + this.totalStaked + "\n"
    }

    override def clone(): StakingState = 
        new StakingState(
            this.stakingConfig,
            this.plasmaParameters,
            this.plasmaMap.copy(),
            this.totalStaked,
            this.sortedKeys.clone()
        )
}   

object StakingState {
    def apply(stakingConfig: StakingConfig, plasmaParameters: PlasmaParameters = PlasmaParameters.default, totalStaked: Long = 0): StakingState =
        new StakingState(
            stakingConfig = stakingConfig, 
            plasmaParameters = plasmaParameters, 
            plasmaMap = new PlasmaMap[ErgoId,Long](
                flags = AvlTreeFlags.AllOperationsAllowed,
                params = plasmaParameters),
            totalStaked = totalStaked,
            sortedKeys = SortedSet[String]())
}
