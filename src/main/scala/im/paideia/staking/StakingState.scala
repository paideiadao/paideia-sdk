package im.paideia.staking

import work.lithos.plasma.PlasmaParameters
import work.lithos.plasma.collections.PlasmaMap
import scala.collection.mutable.SortedSet
import sigmastate.AvlTreeFlags
import org.ergoplatform.sdk.ErgoId
import work.lithos.plasma.ByteConversion.convertsLongVal
import work.lithos.plasma.collections.ProvenResult
import scala.util.Failure
import scala.util.Success
import im.paideia.DAOConfig
import work.lithos.plasma.collections.ProxyPlasmaMap
import scorex.crypto.hash.Blake2b256
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import scorex.db.LDBVersionedStore
import java.io.File
import scorex.crypto.hash.Digest32
import org.apache.commons.io.FileUtils
import im.paideia.util.MempoolPlasmaMap
import scorex.crypto.authds.ADDigest
import im.paideia.util.ProvenResultWithDigest

class StakingState(
  val emissionTime: Long,
  val current: Boolean,
  val stakeRecords: MempoolPlasmaMap[ErgoId, StakeRecord],
  val participationRecords: MempoolPlasmaMap[ErgoId, ParticipationRecord]
) {

  def totalStaked(digestOpt: Option[ADDigest] = None): Long = {
    stakeRecords
      .getMap(digestOpt)
      .get
      .toMap
      .values
      .foldLeft(0L)((z: Long, stakeRecord: StakeRecord) => stakeRecord.stake + z)
  }

  def stakers(digestOpt: Option[ADDigest] = None): Long = {
    stakeRecords
      .getMap(digestOpt)
      .get
      .toMap
      .size
  }

  def sortedKeys(digestOpt: Option[ADDigest] = None): SortedSet[String] = {
    SortedSet(stakeRecords.getMap(digestOpt).get.toMap.keys.map(_.toString()).toSeq: _*)
  }

  def stake(
    stakingKey: String,
    stakeRecord: StakeRecord,
    digestOrHeight: Either[ADDigest, Int],
    inPlace: Boolean = false
  ): ProvenResultWithDigest[StakeRecord] = {
    stakeRecords.insertWithDigest((ErgoId.create(stakingKey), stakeRecord))(
      digestOrHeight,
      inPlace
    )
  }

  def unstake(
    stakingKeys: List[String],
    digestOrHeight: Either[ADDigest, Int]
  ): ProvenResultWithDigest[StakeRecord] = {
    stakeRecords.deleteWithDigest(
      stakingKeys.map((stakingKey: String) => ErgoId.create(stakingKey)): _*
    )(digestOrHeight)
  }

  def getStakes(
    stakingKeys: List[String],
    digestOpt: Option[ADDigest]
  ): ProvenResult[StakeRecord] = {
    stakeRecords
      .lookUpWithDigest(stakingKeys.map(key => ErgoId.create(key)): _*)(digestOpt)
  }

  def getStake(stakingKey: String, digestOpt: Option[ADDigest]): StakeRecord = {
    getStakes(List[String](stakingKey), digestOpt).response(0).tryOp match {
      case Failure(exception) => throw exception
      case Success(value)     => value.get
    }
  }

  def getParticipations(
    stakingKeys: List[String],
    digestOpt: Option[ADDigest]
  ): ProvenResult[ParticipationRecord] = {
    participationRecords
      .lookUpWithDigest(stakingKeys.map(key => ErgoId.create(key)): _*)(digestOpt)
  }

  def changeStakes(
    newStakes: List[(String, StakeRecord)],
    digestOrHeight: Either[ADDigest, Int]
  ): ProvenResultWithDigest[StakeRecord] = {
    stakeRecords.updateWithDigest(
      newStakes.map(kv => (ErgoId.create(kv._1), kv._2)): _*
    )(digestOrHeight)
  }

  def insertParticipations(
    newParticipation: List[(String, ParticipationRecord)],
    digestOrHeight: Either[ADDigest, Int]
  ): ProvenResultWithDigest[ParticipationRecord] = {
    participationRecords.insertWithDigest(
      newParticipation.map(kv => (ErgoId.create(kv._1), kv._2)): _*
    )(digestOrHeight)
  }

  def changeParticipations(
    newParticipation: List[(String, ParticipationRecord)],
    digestOrHeight: Either[ADDigest, Int]
  ): ProvenResultWithDigest[ParticipationRecord] = {
    participationRecords.updateWithDigest(
      newParticipation.map(kv => (ErgoId.create(kv._1), kv._2)): _*
    )(digestOrHeight)
  }

  def getKeys(from: Int = 0, n: Int = 1, digestOpt: Option[ADDigest]): List[String] =
    sortedKeys(digestOpt).toList.slice(from, from + n)

  def size(digestOpt: Option[ADDigest]): Int = sortedKeys(digestOpt).size

  override def toString: String = {
    "State:\n" +
    "Number of stakers: " + size(None).toString + "\n" +
    "Total staked: " + totalStaked() + "\n"
  }

  def clone(
    daoKey: String,
    newEmissionTime: Long
  ): StakingState = {
    val newStorages = List("stake", "participation").map(f => {
      val folder = new File(
        "./stakingStates/" ++ daoKey ++ "/" ++ f ++ "/" ++ (if (current) "current"
                                                            else emissionTime.toString)
      )
      val newFolder = new File(
        "./stakingStates/" ++ daoKey ++ "/" ++ f ++ "/" ++ newEmissionTime.toString
      )
      newFolder.mkdirs()
      FileUtils.copyDirectory(folder, newFolder)
      val ldbStore = new LDBVersionedStore(newFolder, 10)
      new VersionedLDBAVLStorage[Digest32](
        ldbStore,
        PlasmaParameters.default.toNodeParams
      )(Blake2b256)
    })

    new StakingState(
      newEmissionTime,
      false,
      stakeRecords.copy(newStorages(0)),
      participationRecords.copy(newStorages(1))
    )

  }
}

object StakingState {

  def apply(
    daoKey: String,
    emissionTime: Long,
    current: Boolean,
    totalStaked: Long = 0
  ): StakingState = {
    val newStorages = List("stake", "participation").map(f => {
      val folder = new File(
        "./stakingStates/" ++ daoKey ++ "/" ++ f ++ "/" ++ (if (current) "current"
                                                            else emissionTime.toString)
      )
      folder.mkdirs()
      val ldbStore = new LDBVersionedStore(folder, 10)
      new VersionedLDBAVLStorage[Digest32](
        ldbStore,
        PlasmaParameters.default.toNodeParams
      )(Blake2b256)
    })
    new StakingState(
      emissionTime,
      current,
      new MempoolPlasmaMap[ErgoId, StakeRecord](
        newStorages(0),
        flags  = AvlTreeFlags.AllOperationsAllowed,
        params = PlasmaParameters.default
      ),
      new MempoolPlasmaMap[ErgoId, ParticipationRecord](
        newStorages(1),
        flags  = AvlTreeFlags.AllOperationsAllowed,
        params = PlasmaParameters.default
      )
    )
  }
}
