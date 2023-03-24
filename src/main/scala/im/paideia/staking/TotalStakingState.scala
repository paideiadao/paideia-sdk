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
import scorex.crypto.authds.ADDigest
import im.paideia.util.MempoolPlasmaMap

class TotalStakingState(
  val daoConfig: DAOConfig,
  var currentStakingState: StakingState,
  val snapshots: HashMap[Long, StakingState]
) {

  def firstMatchingSnapshot(
    snapshotDigest: ADDigest
  ): StakingState = {
    snapshots
      .get(
        snapshots.keys.toSeq
          .sortBy(l => l)
          .find(ss =>
            snapshots.get(ss).get.plasmaMap.getMap(Some(snapshotDigest)).isDefined
          )
          .get
      )
      .get
  }

  override def toString(): String = {
    this.currentStakingState.toString() +
    "Snapshots: " + this.snapshots.size.toString + "\n"
  }
}

object TotalStakingState {

  val _stakingStates: HashMap[String, TotalStakingState] =
    HashMap[String, TotalStakingState]()

  def apply(
    daoKey: String,
    nextEmission: Long,
    clearAll: Boolean = false
  ): TotalStakingState = {
    if (clearAll) _stakingStates.clear()
    val daoConfig    = Paideia.getConfig(daoKey)
    val currentState = StakingState(daoKey, nextEmission, true)
    val profitTokensSize =
      daoConfig.getArray[Array[Byte]](ConfKeys.im_paideia_staking_profit_tokenids).size
    val cycleLength = daoConfig[Long](ConfKeys.im_paideia_staking_cyclelength)
    val snapshots =
      HashMap[Long, StakingState](
        Range(0, daoConfig[Long](ConfKeys.im_paideia_staking_emission_delay).toInt)
          .map(i => {
            val emissionTime: Long = nextEmission - (i + 1) * cycleLength
            (emissionTime, currentState.clone(daoKey, emissionTime))
          }): _*
      )

    val newState = new TotalStakingState(
      daoConfig,
      currentState,
      snapshots
    )
    _stakingStates.put(daoKey, newState)
    newState
  }

  def apply(daoKey: String): TotalStakingState = _stakingStates(daoKey)
}
