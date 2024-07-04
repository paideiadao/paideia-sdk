package im.paideia.staking

import scala.collection.mutable.Queue
import work.lithos.plasma.collections.ProvenResult
import work.lithos.plasma.collections.OpResult
import work.lithos.plasma.ByteConversion
import scala.util.Failure
import scala.util.Success
import sigma.Coll
import org.ergoplatform.sdk.ErgoId
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
    snapshotDigest: ADDigest,
    participationDigest: ADDigest
  ): StakingState = {
    snapshots
      .get(
        snapshots.keys.toSeq
          // .sortBy(l => -l)
          .find(ss =>
            snapshots.get(ss).get.stakeRecords.getMap(Some(snapshotDigest)).isDefined &&
              snapshots
                .get(ss)
                .get
                .participationRecords
                .getMap(Some(participationDigest))
                .isDefined
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
    val daoConfig        = Paideia.getConfig(daoKey)
    val currentState     = StakingState(daoKey, nextEmission, true)
    val profitTokensSize = 0
    // daoConfig.getArray[Object](ConfKeys.im_paideia_staking_profit_tokenids).size
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
