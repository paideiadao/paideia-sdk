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

class StakingState(
  daoKey: String,
  emissionTime: Long,
  plasmaParameters: PlasmaParameters,
  val plasmaMap: ProxyPlasmaMap[ErgoId, StakeRecord],
  var totalStaked: Long,
  sortedKeys: SortedSet[String]
) {

  def stake(
    stakingKey: String,
    stakeRecord: StakeRecord
  ): ProvenResult[StakeRecord] = {
    if (!plasmaMap.getTempMap.isDefined) initiate
    this.totalStaked += stakeRecord.stake
    this.sortedKeys.add(stakingKey) match {
      case true =>
        this.plasmaMap.insert((ErgoId.create(stakingKey), stakeRecord))
      case false => throw new RuntimeException
    }
  }

  def unstake(stakingKeys: List[String]): ProvenResult[StakeRecord] = {
    stakingKeys.foreach(stakingKey => {
      this.totalStaked -= this.getStake(stakingKey).stake
      this.sortedKeys.remove(stakingKey)
    })
    if (!plasmaMap.getTempMap.isDefined) initiate
    this.plasmaMap.delete(
      stakingKeys.map((stakingKey: String) => ErgoId.create(stakingKey)): _*
    )
  }

  def getStakes(stakingKeys: List[String]): ProvenResult[StakeRecord] = {
    if (!plasmaMap.getTempMap.isDefined) initiate
    plasmaMap.lookUp(stakingKeys.map(key => ErgoId.create(key)): _*)
  }

  def getStake(stakingKey: String): StakeRecord = {
    if (!plasmaMap.getTempMap.isDefined) initiate
    this.getStakes(List[String](stakingKey)).response(0).tryOp match {
      case Failure(exception) => throw exception
      case Success(value)     => value.get
    }
  }

  def changeStakes(
    newStakes: List[(String, StakeRecord)]
  ): ProvenResult[StakeRecord] = {
    if (!plasmaMap.getTempMap.isDefined) initiate
    val currentStakes = this.getStakes(newStakes.map(kv => kv._1))
    this.totalStaked = this.totalStaked -
      currentStakes.response.foldLeft(0L)((x, y) => x + y.tryOp.get.get.stake) +
      newStakes.foldLeft(0L)((x, kv) => x + kv._2.stake)
    this.plasmaMap.update(
      newStakes.map(kv => (ErgoId.create(kv._1), kv._2)): _*
    )
  }

  def getKeys(from: Int = 0, n: Int = 1): List[String] =
    this.sortedKeys.toList.slice(from, from + n)

  def size(): Int = this.sortedKeys.size

  override def toString: String = {
    "State:\n" +
    "Number of stakers: " + this.size.toString + "\n" +
    "Total staked: " + this.totalStaked + "\n"
  }

  def initiate = {
    plasmaMap.initiate()
    plasmaMap.getTempMap.get.prover.generateProof()
  }

  def clone(newEmissionTime: Long): StakingState = {
    if (!plasmaMap.getTempMap.isDefined) plasmaMap.initiate()
    plasmaMap.commitChanges()
    val folder = new File(
      "./stakingStates/" ++ daoKey ++ "/" ++ emissionTime.toString()
    )
    val newFolder = new File(
      "./stakingStates/" ++ daoKey ++ "/" ++ newEmissionTime.toString()
    )
    newFolder.mkdirs()
    FileUtils.copyDirectory(folder, newFolder)
    val ldbStore = new LDBVersionedStore(newFolder, 10)
    val avlStorage = new VersionedLDBAVLStorage[Digest32](
      ldbStore,
      PlasmaParameters.default.toNodeParams
    )(Blake2b256)
    new StakingState(
      daoKey,
      newEmissionTime,
      plasmaParameters = plasmaParameters,
      plasmaMap = new ProxyPlasmaMap[ErgoId, StakeRecord](
        avlStorage,
        flags  = AvlTreeFlags.AllOperationsAllowed,
        params = plasmaParameters
      ),
      totalStaked = totalStaked,
      this.sortedKeys.clone()
    )
  }
}

object StakingState {

  def apply(
    daoKey: String,
    emissionTime: Long,
    plasmaParameters: PlasmaParameters = PlasmaParameters.default,
    totalStaked: Long                  = 0
  ): StakingState = {
    val folder = new File("./stakingStates/" ++ daoKey ++ "/" ++ emissionTime.toString())
    folder.mkdirs()
    val ldbStore = new LDBVersionedStore(folder, 10)
    val avlStorage = new VersionedLDBAVLStorage[Digest32](
      ldbStore,
      PlasmaParameters.default.toNodeParams
    )(Blake2b256)
    new StakingState(
      daoKey,
      emissionTime,
      plasmaParameters = plasmaParameters,
      plasmaMap = new ProxyPlasmaMap[ErgoId, StakeRecord](
        avlStorage,
        flags  = AvlTreeFlags.AllOperationsAllowed,
        params = plasmaParameters
      ),
      totalStaked = totalStaked,
      sortedKeys  = SortedSet[String]()
    )
  }
}
