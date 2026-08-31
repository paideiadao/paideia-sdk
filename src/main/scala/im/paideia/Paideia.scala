package im.paideia

import scala.collection.mutable.HashMap
import scala.collection.mutable.Set
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContract
import im.paideia.util.Env
import im.paideia.util.Util
import im.paideia.util.MempoolPlasmaMap
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import scala.reflect.runtime.{universe => ru}
import im.paideia.common.filtering.FilterNode
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.Proposal
import im.paideia.governance.contracts.ProposalContract
import im.paideia.staking.StakingState
import im.paideia.staking.TotalStakingState
import scorex.crypto.hash.Blake2b256
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.apache.commons.io.FileUtils
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.impl.ScalaBridge
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import scala.collection.JavaConverters._

/** Thrown internally by Paideia.restoreState when a persisted digest doesn't match what
  * was actually reopened from disk (a stale or partial checkpoint, or a tampered
  * state.json). Always caught inside restoreState and converted to None; its message is
  * also made available via Paideia.lastRestoreError.
  */
class PaideiaRestoreException(message: String) extends Exception(message)

object Paideia {
  var _daoMap: HashMap[String, DAO] = HashMap[String, DAO]()

  var _actorList: HashMap[String, PaideiaActor] = HashMap[String, PaideiaActor]()

  /** Reason the most recent restoreState call returned None because of a digest
    * mismatch or other restore-time failure; None otherwise (including after a
    * successful restore, or a call that returned None simply because state.json didn't
    * exist).
    */
  var lastRestoreError: Option[String] = None

  /** Fingerprint (hex Blake2b256 of the sorted, comma-joined confirmed box ids) of the
    * box set last written to disk for a given (daoKey, contract signature hash hex), so
    * persistState only rewrites a box file when its contents actually changed.
    */
  private val _boxFileFingerprints: HashMap[(String, String), String] =
    HashMap[(String, String), String]()

  private val gson = new Gson()

  def clear = {
    _actorList.values.foreach(_.clear)
    _daoMap    = HashMap[String, DAO]()
    _actorList = HashMap[String, PaideiaActor]()
    _boxFileFingerprints.clear()
    FileUtils.deleteDirectory(new File("./daoconfigs"))
    FileUtils.deleteDirectory(new File("./proposals"))
    FileUtils.deleteDirectory(new File("./stakingStates"))
  }

  /** Clears every in-process registry (_daoMap, every actor's contractInstances,
    * TotalStakingState._stakingStates) WITHOUT deleting any on-disk directory - unlike
    * clear, which wipes ./daoconfigs, ./proposals and ./stakingStates. Used before
    * restoreState (which must start from empty registries) and by tests that want to
    * prove restored data actually comes back from disk rather than from still-live
    * in-memory state.
    *
    * @param closeStores
    *   when true, also closes every live MempoolPlasmaMap's underlying LevelDB handle
    *   (MempoolPlasmaMap.closeAll()), releasing its LOCK file so the same directory can
    *   be reopened by a fresh store afterwards - LevelDB refuses to reopen a directory
    *   while another handle still holds the lock.
    */
  def clearRegistries(closeStores: Boolean): Unit = {
    if (closeStores) MempoolPlasmaMap.closeAll()
    _actorList.values.foreach(_.clear)
    _daoMap    = HashMap[String, DAO]()
    _actorList = HashMap[String, PaideiaActor]()
    TotalStakingState._stakingStates.clear()
  }

  /** Commits every live MempoolPlasmaMap (DAOConfig, Proposal vote records,
    * StakingState stake/participation records, ...), draining their queued confirmed
    * mutations into the versioned AVL+ prover so they're actually persisted to disk.
    * Call once per confirmed block, after all of that block's transactions have been
    * handled. Returns the number of maps committed.
    */
  def commit(): Int = MempoolPlasmaMap.commitAll()

  def addDAO(dao: DAO): Unit = _daoMap.put(dao.key, dao)

  def getDAO(key: String): DAO = _daoMap(key)

  def initialize: Unit = {
    val paideiaConfig = DAOConfig(Env.paideiaDaoKey)

    addDAO(DAO(Env.paideiaDaoKey, paideiaConfig))
  }

  def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
    PaideiaEventResponse.merge(_actorList.values.map {
      _.handleEvent(event)
    }.toList)
  }

  def getActor[T <: PaideiaActor](className: String): PaideiaActor =
    _actorList(className).asInstanceOf[T]

  def instantiateActor(contractSignature: PaideiaContractSignature) = {
    if (!_actorList.contains(contractSignature.className)) {
      val m    = ru.runtimeMirror(getClass.getClassLoader)
      val inst = m.reflectModule(m.staticModule(contractSignature.className)).instance
      inst match {
        case pa: PaideiaActor => _actorList.put(contractSignature.className, pa)
      }
    }
  }

  def instantiateContractInstance(contractSignature: PaideiaContractSignature) = {
    instantiateActor(contractSignature)
    _actorList(contractSignature.className)(contractSignature)
  }

  def getBox(boxFilter: FilterNode): List[InputBox] = {
    _actorList.values.toList.flatMap(_.getBox(boxFilter))
  }

  def getConfig(daoKey: String): DAOConfig = _daoMap(daoKey).config

  def getProposalContract(contractHash: List[Byte]): ProposalContract = {
    _actorList.values
      .find(_.getProposalContract(contractHash).isSuccess)
      .get
      .getProposalContract(contractHash)
      .get
  }

  def getBoxById(boxId: String): Option[InputBox] = {
    _actorList.values
      .flatMap(_.contractInstances.values)
      .find(_.getBoxes.contains(boxId))
      .flatMap(_.boxes.get(boxId))
  }

  private def findContractInstance(
    daoKey: String,
    contractHashHex: String
  ): Option[PaideiaContract] =
    _actorList.values
      .flatMap(_.contractInstances.values)
      .find(pc =>
        pc.contractSignature.daoKey == daoKey &&
          Util
            .bytes2hex(pc.contractSignature.contractHash.toArray)
            .equalsIgnoreCase(contractHashHex)
      )

  /** Recovers the ErgoTransactionOutput a confirmed InputBox was originally built from,
    * for persistState to serialize with Gson (same approach paideia-state's transaction
    * archive uses for whole ErgoTransactions). Every box PaideiaContract ever adds is
    * constructed as `new InputBoxImpl(output: ErgoTransactionOutput)` (see
    * PaideiaContract.handleEvent), so going through InputBoxImpl.getErgoBox() and back
    * through appkit's own Iso (ScalaBridge.isoErgoTransactionOutput) round-trips
    * losslessly rather than hand-reconstructing the REST model.
    */
  private def toErgoTransactionOutput(box: InputBox): ErgoTransactionOutput =
    box match {
      case impl: InputBoxImpl =>
        ScalaBridge.isoErgoTransactionOutput.from(impl.getErgoBox())
      case other =>
        throw new IllegalStateException(
          "Paideia.persistState: cannot serialize a box of type " +
            other.getClass.getName +
            "; only InputBoxImpl (as constructed by PaideiaContract.newBox) is supported"
        )
    }

  private def boxSetFingerprint(ids: Iterable[String]): String =
    Util.bytes2hex(
      Blake2b256(ids.toList.sorted.mkString(",").getBytes(StandardCharsets.UTF_8)).array
    )

  /** Atomically writes `content` to `file`: writes to a sibling `<name>.tmp` first, then
    * Files.move's it into place, so a crash mid-write never leaves a half-written
    * state.json or box file behind.
    */
  private def atomicWriteString(file: File, content: String): Unit = {
    file.getParentFile.mkdirs()
    val tmp = new File(file.getParentFile, file.getName + ".tmp")
    Files.write(tmp.toPath, content.getBytes(StandardCharsets.UTF_8))
    Files.move(
      tmp.toPath,
      file.toPath,
      StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING
    )
  }

  /** Persists everything that today only lives in process memory and is otherwise
    * rebuilt from scratch by replaying the whole transaction archive on every restart:
    * the DAO/proposal/contract-instance registries (as digests to verify against, plus
    * enough to reopen every persisted AVL+ store) and each contract instance's confirmed
    * unspent box set. Call AFTER Paideia.commit() so every digest recorded here is
    * actually backed by what's on disk.
    *
    * Writes dir/state.json and dir/boxes/<daoKey>/<contractSignatureHashHex>.json (one
    * JSON array of ErgoTransactionOutput per contract instance's confirmed unspent
    * boxes). Both are written atomically; box files are only rewritten when their
    * contents actually changed since the last call (tracked via an in-memory
    * fingerprint), and files for contract instances that no longer exist are deleted.
    *
    * dir/state.json layout: `{ "height": Int, "daos": [ { "key": String,
    * "configDigest": hex String, "contracts": [ { "className": String, "version":
    * String, "networkType": "MAINNET"|"TESTNET", "contractHash": hex String, "daoKey":
    * String }, ... ], "proposals": [ { "index": Int, "name": String, "votesDigest": hex
    * String }, ... ], "staking": null | { "nextEmission": Long, "currentDigests": {
    * "stake": hex String, "participation": hex String }, "snapshots": [ {
    * "emissionTime": Long, "stakeDigest": hex String, "participationDigest": hex String
    * }, ... ] } }, ... ], "knownKeys": [ { "hash": hex String, "name": String }, ... ] }`.
    * "knownKeys" is every DAOConfigKey.knownKeys entry with a defined name (hashed key
    * bytes -> the original name it was constructed with), sorted by hash for stable
    * output - see restoreState for why this has to be persisted rather than recomputed.
    * "contracts" is every PaideiaContractSignature actually live
    * in Paideia._actorList's contractInstances for this daoKey at persist time - not a
    * walk of the DAO config tree, which only reaches contract instances the config
    * happens to reference right now and misses instances created by direct construction
    * (e.g. `Stake(PaideiaContractSignature(daoKey = ...))`), proxy contracts, or the
    * longLivingKey re-instantiation path in PaideiaContract.handleEvent - any of which
    * can have a box file that a config-tree walk would then fail to find a home for on
    * restore.
    */
  def persistState(dir: File, height: Int): Unit = {
    dir.mkdirs()

    val stateJson = new JsonObject()
    stateJson.addProperty("height", height)
    val daosArr = new JsonArray()

    _daoMap.toList.sortBy(_._1).foreach { case (daoKey, dao) =>
      val daoObj = new JsonObject()
      daoObj.addProperty("key", daoKey)
      daoObj.addProperty("configDigest", Util.bytes2hex(dao.config._config.digest))

      // The actual live contract instances for this DAO, not just the ones the config
      // tree happens to reference right now: contract instances also come from direct
      // construction (e.g. Stake(PaideiaContractSignature(...))) registered through
      // PaideiaActor.getContractInstance, proxy contracts, and the longLivingKey
      // re-instantiation in PaideiaContract.handleEvent (which can leave an outdated
      // instance alongside its replacement). Recording every one of them - rather than
      // re-walking the config tree on restore - is what lets restoreState recreate
      // exactly the instances that had box files written for them.
      val contractsArr = new JsonArray()
      _actorList.values
        .flatMap(_.contractInstances.values)
        .filter(_.contractSignature.daoKey == daoKey)
        .toList
        .sortBy(instance =>
          (
            instance.contractSignature.className,
            Util.bytes2hex(instance.contractSignature.contractHash.toArray)
          )
        )
        .foreach { instance =>
          val sig         = instance.contractSignature
          val contractObj = new JsonObject()
          contractObj.addProperty("className", sig.className)
          contractObj.addProperty("version", sig.version)
          contractObj.addProperty("networkType", sig.networkType.toString())
          contractObj.addProperty(
            "contractHash",
            Util.bytes2hex(sig.contractHash.toArray)
          )
          contractObj.addProperty("daoKey", sig.daoKey)
          contractsArr.add(contractObj)
        }
      daoObj.add("contracts", contractsArr)

      val proposalsArr = new JsonArray()
      dao.proposals.toList.sortBy(_._1).foreach { case (index, proposal) =>
        val proposalObj = new JsonObject()
        proposalObj.addProperty("index", index)
        proposalObj.addProperty("name", proposal.name)
        proposalObj.addProperty("votesDigest", Util.bytes2hex(proposal.votes.digest))
        proposalsArr.add(proposalObj)
      }
      daoObj.add("proposals", proposalsArr)

      TotalStakingState._stakingStates.get(daoKey) match {
        case None => daoObj.add("staking", JsonNull.INSTANCE)
        case Some(tss) =>
          val stakingObj = new JsonObject()
          stakingObj.addProperty("nextEmission", tss.currentStakingState.emissionTime)
          val currentDigestsObj = new JsonObject()
          currentDigestsObj.addProperty(
            "stake",
            Util.bytes2hex(tss.currentStakingState.stakeRecords.digest)
          )
          currentDigestsObj.addProperty(
            "participation",
            Util.bytes2hex(tss.currentStakingState.participationRecords.digest)
          )
          stakingObj.add("currentDigests", currentDigestsObj)
          val snapshotsArr = new JsonArray()
          tss.snapshots.toList.sortBy(_._1).foreach { case (emissionTime, snapshot) =>
            val snapshotObj = new JsonObject()
            snapshotObj.addProperty("emissionTime", emissionTime)
            snapshotObj.addProperty(
              "stakeDigest",
              Util.bytes2hex(snapshot.stakeRecords.digest)
            )
            snapshotObj.addProperty(
              "participationDigest",
              Util.bytes2hex(snapshot.participationRecords.digest)
            )
            snapshotsArr.add(snapshotObj)
          }
          stakingObj.add("snapshots", snapshotsArr)
          daoObj.add("staking", stakingObj)
      }

      daosArr.add(daoObj)
    }
    stateJson.add("daos", daosArr)

    // DAOConfigKey.knownKeys (hashed key -> original name) only gets populated for a
    // key when it's constructed BY NAME (DAOConfigKey(s) / DAOConfigKey(s, bytes)); a
    // key deserialized straight from a persisted AVL+ tree - as every key is after
    // restoreState - never goes through that constructor, so without persisting this
    // map here every dynamic key (e.g. im.paideia.contracts.proposal.<hex>) would come
    // back nameless. The standard ConfKeys names are reconstructed for free (ConfKeys
    // registers them at class-init time), but dynamic per-proposal/per-action names are
    // base ++ hex(bytes) and the bytes aren't recoverable from the hash alone, so they
    // must be persisted rather than recomputed.
    val knownKeysArr = new JsonArray()
    DAOConfigKey.knownKeys.toList
      .collect { case (hashedKey, Some(name)) => (hashedKey, name) }
      .sortBy(kv => Util.bytes2hex(kv._1.toArray))
      .foreach { case (hashedKey, name) =>
        val entry = new JsonObject()
        entry.addProperty("hash", Util.bytes2hex(hashedKey.toArray))
        entry.addProperty("name", name)
        knownKeysArr.add(entry)
      }
    stateJson.add("knownKeys", knownKeysArr)

    atomicWriteString(new File(dir, "state.json"), gson.toJson(stateJson))

    val boxesRoot = new File(dir, "boxes")
    val liveKeys  = scala.collection.mutable.Set[(String, String)]()

    _daoMap.keys.foreach { daoKey =>
      _actorList.values
        .flatMap(_.contractInstances.values)
        .filter(_.contractSignature.daoKey == daoKey)
        .foreach { instance =>
          val sigHashHex =
            Util.bytes2hex(instance.contractSignature.contractHash.toArray)
          val cacheKey = (daoKey, sigHashHex)
          liveKeys.add(cacheKey)

          val confirmedIds =
            (instance.utxos.toSet intersect instance.boxes.keySet.toSet).toList.sorted
          val fingerprint = boxSetFingerprint(confirmedIds)

          if (!_boxFileFingerprints.get(cacheKey).contains(fingerprint)) {
            val outputsArr = new JsonArray()
            confirmedIds.foreach(id =>
              outputsArr.add(gson.toJsonTree(toErgoTransactionOutput(instance.boxes(id))))
            )
            atomicWriteString(
              new File(new File(boxesRoot, daoKey), sigHashHex + ".json"),
              gson.toJson(outputsArr)
            )
            _boxFileFingerprints(cacheKey) = fingerprint
          }
        }
    }

    if (boxesRoot.exists()) {
      Option(boxesRoot.listFiles()).getOrElse(Array[File]()).filter(_.isDirectory).foreach {
        daoDir =>
          val daoKey = daoDir.getName
          Option(daoDir.listFiles())
            .getOrElse(Array[File]())
            .filter(_.getName.endsWith(".json"))
            .foreach { f =>
              val sigHashHex = f.getName.stripSuffix(".json")
              if (!liveKeys.contains((daoKey, sigHashHex))) {
                f.delete()
                _boxFileFingerprints.remove((daoKey, sigHashHex))
              }
            }
      }
    }
  }

  /** Rebuilds every in-process registry (DAOs/configs, contract instances, proposals,
    * staking state), re-registers every persisted DAOConfigKey.knownKeys (hash -> name)
    * entry so dynamic config keys resolve their names exactly as they did before the
    * restart, and every contract instance's confirmed unspent box set from a
    * checkpoint written by persistState, verifying every digest against what's actually
    * reopened from the persisted AVL+ stores on disk along the way, and every recorded
    * contract instance by recompiling it from its signature and comparing the resulting
    * ErgoTree hash against what was recorded. Must be called on a fresh process state
    * (e.g. right after Paideia.clearRegistries) before any mutation - it unconditionally
    * addDAO/instantiates/registers as it goes.
    *
    * Returns None (and leaves every registry empty, and lastRestoreError set to the
    * reason) if dir/state.json doesn't exist, or if any digest or contract hash fails to
    * verify against what was actually reopened/recompiled - a stale or partial
    * checkpoint degrades to a full replay instead of silently resurrecting the wrong
    * state. Returns Some(height) on success.
    */
  def restoreState(dir: File): Option[Int] = {
    lastRestoreError = None
    val stateFile = new File(dir, "state.json")
    if (!stateFile.exists()) return None

    try {
      val stateJson = new JsonParser()
        .parse(new String(Files.readAllBytes(stateFile.toPath), StandardCharsets.UTF_8))
        .getAsJsonObject
      val height = stateJson.get("height").getAsInt

      // Re-register every persisted (hash -> name) pair into the process-global
      // DAOConfigKey.knownKeys FIRST, before any DAOConfig/DAOConfigKey is constructed
      // below: knownKeys is only ever populated when a key is built BY NAME, and every
      // key that comes back out of a restored AVL+ tree is built from raw hashed bytes
      // instead (see DAOConfigKey.convertsDAOConfigKey), so without this step every
      // dynamic key (e.g. im.paideia.contracts.proposal.<hex>) would resolve to no name
      // at all post-restart, even though it had one before. Missing "knownKeys" (an
      // older checkpoint written before this field existed) is treated as empty, not an
      // error.
      Option(stateJson.get("knownKeys"))
        .filterNot(_.isJsonNull)
        .foreach(_.getAsJsonArray.asScala.foreach { knownKeyElem =>
          val knownKeyObj = knownKeyElem.getAsJsonObject
          val hashBytes   = Util.hex2bytes(knownKeyObj.get("hash").getAsString)
          val name        = knownKeyObj.get("name").getAsString
          DAOConfigKey.knownKeys.put(hashBytes.toList, Some(name))
        })

      stateJson.get("daos").getAsJsonArray.asScala.foreach { daoElem =>
        val daoObj                  = daoElem.getAsJsonObject
        val daoKey                  = daoObj.get("key").getAsString
        val expectedConfigDigestHex = daoObj.get("configDigest").getAsString

        val config = DAOConfig(daoKey)
        if (
          !Util
            .bytes2hex(config._config.digest)
            .equalsIgnoreCase(expectedConfigDigestHex)
        )
          throw new PaideiaRestoreException(
            "restoreState: config digest mismatch for DAO " + daoKey
          )

        val dao = DAO(daoKey, config)
        addDAO(dao)

        // Recreate exactly the contract instances persistState recorded as actually
        // live for this DAO (see the comment there) - not every PaideiaContractSignature
        // reachable by walking the config tree, which misses instances created by direct
        // construction, proxy contracts, or the longLivingKey re-instantiation path.
        // instantiateContractInstance(sig) resolves the actor for sig.className (via
        // instantiateActor) and then calls that actor's apply(contractSignature), which -
        // for every PaideiaContract subtype in this codebase - is
        // getContractInstance[T](sig, new T(sig)): it builds a fresh instance from just
        // className/version/networkType/daoKey (recompiling its ErgoTree, using
        // sig.daoKey to pull whatever config values the contract's parameters need) and
        // recomputes contractSignature.contractHash from that compiled tree - sig's own
        // contractHash field is never consulted for construction. So reproducing the
        // recorded contractHash here is exactly the same compile-and-verify performed
        // for a fresh instance at runtime; a mismatch means the running system's contract
        // code changed underneath the checkpoint.
        daoObj.get("contracts").getAsJsonArray.asScala.foreach { contractElem =>
          val contractObj = contractElem.getAsJsonObject
          val className   = contractObj.get("className").getAsString
          val version     = contractObj.get("version").getAsString
          val networkType =
            NetworkType.valueOf(contractObj.get("networkType").getAsString)
          val expectedHashHex = contractObj.get("contractHash").getAsString
          val sigDaoKey       = contractObj.get("daoKey").getAsString

          val sig = PaideiaContractSignature(
            className,
            version,
            networkType,
            List(0.toByte),
            sigDaoKey
          )
          val instance = instantiateContractInstance(sig)
          val actualHashHex =
            Util.bytes2hex(instance.contractSignature.contractHash.toArray)
          if (!actualHashHex.equalsIgnoreCase(expectedHashHex))
            throw new PaideiaRestoreException(
              "restoreState: contract hash mismatch for DAO " + daoKey +
                " contract " + className + " (recorded " + expectedHashHex +
                ", recompiled " + actualHashHex + ")"
            )
        }

        daoObj.get("proposals").getAsJsonArray.asScala.foreach { proposalElem =>
          val proposalObj           = proposalElem.getAsJsonObject
          val index                 = proposalObj.get("index").getAsInt
          val name                  = proposalObj.get("name").getAsString
          val expectedVotesDigest = proposalObj.get("votesDigest").getAsString

          val proposal = Proposal(daoKey, index, name)
          if (
            !Util
              .bytes2hex(proposal.votes.digest)
              .equalsIgnoreCase(expectedVotesDigest)
          )
            throw new PaideiaRestoreException(
              "restoreState: votes digest mismatch for DAO " + daoKey + " proposal " +
                index
            )
          dao.proposals(index) = proposal
        }

        val stakingElem = daoObj.get("staking")
        if (stakingElem != null && !stakingElem.isJsonNull) {
          val stakingObj    = stakingElem.getAsJsonObject
          val nextEmission  = stakingObj.get("nextEmission").getAsLong
          val currentDigests = stakingObj.get("currentDigests").getAsJsonObject
          val expectedCurrentStakeDigest = currentDigests.get("stake").getAsString
          val expectedCurrentParticipationDigest =
            currentDigests.get("participation").getAsString

          val currentState = StakingState(daoKey, nextEmission, true)
          if (
            !Util
              .bytes2hex(currentState.stakeRecords.digest)
              .equalsIgnoreCase(expectedCurrentStakeDigest)
          )
            throw new PaideiaRestoreException(
              "restoreState: current stake digest mismatch for DAO " + daoKey
            )
          if (
            !Util
              .bytes2hex(currentState.participationRecords.digest)
              .equalsIgnoreCase(expectedCurrentParticipationDigest)
          )
            throw new PaideiaRestoreException(
              "restoreState: current participation digest mismatch for DAO " + daoKey
            )

          val snapshots = HashMap[Long, StakingState]()
          stakingObj.get("snapshots").getAsJsonArray.asScala.foreach { snapshotElem =>
            val snapshotObj  = snapshotElem.getAsJsonObject
            val emissionTime = snapshotObj.get("emissionTime").getAsLong
            val expectedSnapshotStakeDigest =
              snapshotObj.get("stakeDigest").getAsString
            val expectedSnapshotParticipationDigest =
              snapshotObj.get("participationDigest").getAsString

            val snapshotState = StakingState(daoKey, emissionTime, false)
            if (
              !Util
                .bytes2hex(snapshotState.stakeRecords.digest)
                .equalsIgnoreCase(expectedSnapshotStakeDigest)
            )
              throw new PaideiaRestoreException(
                "restoreState: snapshot stake digest mismatch for DAO " + daoKey +
                  " emissionTime " + emissionTime
              )
            if (
              !Util
                .bytes2hex(snapshotState.participationRecords.digest)
                .equalsIgnoreCase(expectedSnapshotParticipationDigest)
            )
              throw new PaideiaRestoreException(
                "restoreState: snapshot participation digest mismatch for DAO " + daoKey +
                  " emissionTime " + emissionTime
              )
            snapshots.put(emissionTime, snapshotState)
          }

          TotalStakingState._stakingStates.put(
            daoKey,
            new TotalStakingState(config, currentState, snapshots)
          )
        }
      }

      val boxesRoot = new File(dir, "boxes")
      if (boxesRoot.exists()) {
        Option(boxesRoot.listFiles())
          .getOrElse(Array[File]())
          .filter(_.isDirectory)
          .foreach { daoDir =>
            val daoKey = daoDir.getName
            Option(daoDir.listFiles())
              .getOrElse(Array[File]())
              .filter(_.getName.endsWith(".json"))
              .foreach { f =>
                val sigHashHex = f.getName.stripSuffix(".json")
                findContractInstance(daoKey, sigHashHex) match {
                  case None =>
                    throw new PaideiaRestoreException(
                      "restoreState: no contract instance found for box file " +
                        f.getPath
                    )
                  case Some(instance) =>
                    val outputsArr = new JsonParser()
                      .parse(
                        new String(Files.readAllBytes(f.toPath), StandardCharsets.UTF_8)
                      )
                      .getAsJsonArray
                    val restoredIds = scala.collection.mutable.ListBuffer[String]()
                    outputsArr.asScala.foreach { outputElem =>
                      val output =
                        gson.fromJson(outputElem, classOf[ErgoTransactionOutput])
                      val box = new InputBoxImpl(output)
                      instance.newBox(box, mempool = false)
                      restoredIds += box.getId().toString()
                    }
                    _boxFileFingerprints((daoKey, sigHashHex)) =
                      boxSetFingerprint(restoredIds)
                }
              }
          }
      }

      Some(height)
    } catch {
      case e: Exception =>
        lastRestoreError = Some(e.getMessage)
        clearRegistries(closeStores = false)
        None
    }
  }
}
