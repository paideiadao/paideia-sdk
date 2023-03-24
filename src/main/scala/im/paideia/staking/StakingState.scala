package im.paideia.staking

import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.collections.PlasmaMap
import scala.collection.mutable.SortedSet
import sigmastate.AvlTreeFlags
import org.ergoplatform.appkit.ErgoId
import io.getblok.getblok_plasma.ByteConversion.convertsLongVal
import io.getblok.getblok_plasma.collections.ProvenResult
import scala.util.Failure
import scala.util.Success
import im.paideia.DAOConfig
import io.getblok.getblok_plasma.collections.ProxyPlasmaMap
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
  plasmaParameters: PlasmaParameters,
  val plasmaMap: MempoolPlasmaMap[ErgoId, StakeRecord]
) {

  def totalStaked(digestOpt: Option[ADDigest] = None): Long = {
    plasmaMap
      .getMap(digestOpt)
      .get
      .toMap
      .values
      .foldLeft(0L)((z: Long, stakeRecord: StakeRecord) => stakeRecord.stake + z)
  }

  def sortedKeys(digestOpt: Option[ADDigest] = None): SortedSet[String] = {
    SortedSet(plasmaMap.getMap(digestOpt).get.toMap.keys.map(_.toString()).toSeq: _*)
  }

  def stake(
    stakingKey: String,
    stakeRecord: StakeRecord,
    digestOrHeight: Either[ADDigest, Int],
    inPlace: Boolean = false
  ): ProvenResultWithDigest[StakeRecord] = {
    plasmaMap.insertWithDigest((ErgoId.create(stakingKey), stakeRecord))(
      digestOrHeight,
      inPlace
    )
  }

  def unstake(
    stakingKeys: List[String],
    digestOrHeight: Either[ADDigest, Int]
  ): ProvenResultWithDigest[StakeRecord] = {
    plasmaMap.deleteWithDigest(
      stakingKeys.map((stakingKey: String) => ErgoId.create(stakingKey)): _*
    )(digestOrHeight)
  }

  def getStakes(
    stakingKeys: List[String],
    digestOpt: Option[ADDigest]
  ): ProvenResult[StakeRecord] = {
    plasmaMap.lookUpWithDigest(stakingKeys.map(key => ErgoId.create(key)): _*)(digestOpt)
  }

  def getStake(stakingKey: String, digestOpt: Option[ADDigest]): StakeRecord = {
    getStakes(List[String](stakingKey), digestOpt).response(0).tryOp match {
      case Failure(exception) => throw exception
      case Success(value)     => value.get
    }
  }

  def changeStakes(
    newStakes: List[(String, StakeRecord)],
    digestOrHeight: Either[ADDigest, Int]
  ): ProvenResultWithDigest[StakeRecord] = {
    plasmaMap.updateWithDigest(
      newStakes.map(kv => (ErgoId.create(kv._1), kv._2)): _*
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
    val folder = new File(
      "./stakingStates/" ++ daoKey ++ "/" ++ (if (current) "current"
                                              else emissionTime.toString)
    )
    val newFolder = new File(
      "./stakingStates/" ++ daoKey ++ "/" ++ newEmissionTime.toString
    )
    newFolder.mkdirs()
    FileUtils.copyDirectory(folder, newFolder)
    val ldbStore = new LDBVersionedStore(newFolder, 10)
    val avlStorage = new VersionedLDBAVLStorage[Digest32](
      ldbStore,
      PlasmaParameters.default.toNodeParams
    )(Blake2b256)
    new StakingState(
      newEmissionTime,
      false,
      plasmaParameters = plasmaParameters,
      plasmaMap        = plasmaMap.copy(avlStorage)
    )

  }
}

object StakingState {

  def apply(
    daoKey: String,
    emissionTime: Long,
    current: Boolean,
    plasmaParameters: PlasmaParameters = PlasmaParameters.default,
    totalStaked: Long                  = 0
  ): StakingState = {
    val folder = new File(
      "./stakingStates/" ++ daoKey ++ "/" ++ (if (current) "current"
                                              else emissionTime.toString)
    )
    folder.mkdirs()
    val ldbStore = new LDBVersionedStore(folder, 10)
    val avlStorage = new VersionedLDBAVLStorage[Digest32](
      ldbStore,
      PlasmaParameters.default.toNodeParams
    )(Blake2b256)
    new StakingState(
      emissionTime,
      current,
      plasmaParameters = plasmaParameters,
      plasmaMap = new MempoolPlasmaMap[ErgoId, StakeRecord](
        avlStorage,
        flags  = AvlTreeFlags.AllOperationsAllowed,
        params = plasmaParameters
      )
    )
  }
}
