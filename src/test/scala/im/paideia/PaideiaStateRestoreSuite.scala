package im.paideia

import im.paideia.common.PaideiaTestSuite
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.staking.StakeRecord
import im.paideia.staking.StakingTest
import im.paideia.staking.TotalStakingState
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.Unstake
import im.paideia.util.Util
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime

/** Covers Paideia.persistState / Paideia.restoreState / Paideia.clearRegistries
  * (deliverable: persisting what today only lives in process memory - the DAO/proposal/
  * contract-instance registries and each contract instance's confirmed unspent box set -
  * so a restart can reopen it from disk instead of replaying the whole transaction
  * archive).
  *
  * Deliberately avoids Env.paideiaDaoKey (the fixed key nearly every other suite reuses
  * via PaideiaTestSuite.init) and instead builds its own DAO under a fresh random key,
  * like StakingTest.testDAO/DAOConfigPersistenceSuite/StakingStateCloneSuite already do
  *   - so this suite's persist/restore round-trip can never be muddied by another
  *     (possibly still-open, since most suites never close their DAOConfig handle)
  *     suite's state sharing that same well-known path.
  */
class PaideiaStateRestoreSuite extends PaideiaTestSuite {

  test(
    "persistState + restoreState round-trips registries, digests, boxes, staking " +
      "state and proposals, and is idempotent; a tampered checkpoint is rejected"
  ) {
    // Best-effort: release whatever this suite's own session left open from an
    // earlier test in this suite, and start every registry empty, before this test
    // builds and measures its own state. Unlike before PaideiaSession existed, this
    // suite's session.liveMaps is never shared with any other suite (each has its own
    // session via PaideiaSessionFixture), so there's no need to scope/restore a
    // process-wide MempoolPlasmaMap.live registry here any more.
    scala.util.Try(Paideia.clearRegistries(closeStores = true))

    {
      val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
      ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
        override def apply(_ctx: BlockchainContext): Unit = {
          val ctx = _ctx.asInstanceOf[BlockchainContextImpl]

          // Builds a fresh, randomly-keyed DAO with staking config + contracts already
          // wired up (ChangeStake/Stake/Unstake/StakeCompound/StakeVote/StakeSnapshot/
          // StakeProfitShare/StakeState) and a current + 2 snapshot TotalStakingState
          // (emission_delay = 2L, cyclelength = 1000000L).
          val dao = StakingTest.testDAO

          val totalStakingState = TotalStakingState(dao.key)
          totalStakingState.currentStakingState.stake(
            Util.randomKey,
            StakeRecord(100L, 0L, List(0L)),
            Right(0)
          )

          // Give two of the already-instantiated contracts confirmed boxes to persist.
          val stakeContract = Stake(PaideiaContractSignature(daoKey = dao.key))
          stakeContract.newBox(stakeContract.box(ctx, 1000000L).inputBox(), false)
          stakeContract.newBox(stakeContract.box(ctx, 2000000L).inputBox(), false)

          val unstakeContract = Unstake(PaideiaContractSignature(daoKey = dao.key))
          unstakeContract.newBox(unstakeContract.box(ctx).inputBox(), false)

          // A second Stake instance (version "1.0.0" rather than the "latest" version
          // wired into dao's config) built by direct construction through
          // PaideiaActor.getContractInstance, exactly like real code does (e.g.
          // PaideiaContract.handleEvent's longLivingKey re-instantiation, or a proxy
          // contract instantiated ad hoc). Nothing in the DAO config tree ever points at
          // it, so the old config-tree-walk restore couldn't have recreated it even
          // though persistState still wrote it a box file - this reproduces the real-run
          // failure ("no contract instance found for box file ...").
          val outdatedStakeContract =
            Stake(PaideiaContractSignature(version = "1.0.0", daoKey = dao.key))
          outdatedStakeContract.newBox(
            outdatedStakeContract.box(ctx, 3000000L).inputBox(),
            false
          )

          val proposal = dao.newProposal(0, "restore-test-proposal")

          // A dynamic config key (base ++ hex(bytes), like the per-proposal/per-action
          // ConfKeys entries) set directly rather than through a ConfKeys helper - its
          // name only ever exists in DAOConfigKey.knownKeys, never recomputable from the
          // hashed bytes alone, so it's the case that actually exercises the "knownKeys"
          // persistence below rather than just the always-registered ConfKeys names.
          val dynamicKey = DAOConfigKey("im.paideia.test.dynamic.", Array[Byte](1, 2, 3))
          dao.config.set(dynamicKey, 42L)
          val recordedDynamicKeyName = dynamicKey.originalKey.get
          val recordedDynamicKeyHash = dynamicKey.hashedKey

          Paideia.commit()

          val tmpDir = Files.createTempDirectory("paideia-state-restore").toFile
          Paideia.persistState(tmpDir, 4711)

          // Record everything persistState should have captured, before anything is
          // cleared, so it can be checked against what restoreState rebuilds.
          val recordedDaoKeys      = Paideia._daoMap.keySet.toSet
          val recordedConfigDigest = Util.bytes2hex(dao.config._config.digest)
          val recordedInstanceCount =
            Paideia._actorList.values.flatMap(_.contractInstances.values).size
          val recordedStakeHashHex =
            Util.bytes2hex(stakeContract.contractSignature.contractHash.toArray)
          val recordedStakeUtxos   = stakeContract.utxos.toSet
          val recordedUnstakeUtxos = unstakeContract.utxos.toSet
          val recordedOutdatedStakeHashHex = Util.bytes2hex(
            outdatedStakeContract.contractSignature.contractHash.toArray
          )
          val recordedOutdatedStakeUtxos = outdatedStakeContract.utxos.toSet
          val recordedSnapshotTimes      = totalStakingState.snapshots.keySet.toSet
          val recordedCurrentStakeDigest =
            Util.bytes2hex(totalStakingState.currentStakingState.stakeRecords.digest)
          val recordedCurrentParticipationDigest =
            Util.bytes2hex(
              totalStakingState.currentStakingState.participationRecords.digest
            )
          val recordedProposalName = proposal.name

          // Idempotency: persisting again with nothing changed must not rewrite any box
          // file. Backdate every box file's mtime first (rather than sleeping) so any
          // rewrite is unambiguous regardless of filesystem mtime granularity.
          val boxesDir = new File(tmpDir, "boxes" + File.separator + dao.key)
          val boxFiles = boxesDir.listFiles()
          assert(boxFiles != null && boxFiles.length > 0)
          boxFiles.foreach(f =>
            Files.setLastModifiedTime(f.toPath, FileTime.fromMillis(0))
          )

          Paideia.persistState(tmpDir, 4711)
          boxFiles.foreach(f =>
            assert(f.lastModified() == 0L, s"box file $f was rewritten")
          )

          // Prove restored data actually comes from disk: close every store and empty
          // every registry first. DAOConfigKey.knownKeys is name metadata rather than
          // state, so clearRegistries must NOT touch it (real restarts keep it warm from
          // whatever names got constructed earlier in the process) - but that means this
          // test has to clear it itself to actually prove restoreState repopulates it
          // from the checkpoint rather than the assertions below passing on leftover
          // process-global state from before the clear.
          Paideia.clearRegistries(closeStores = true)
          DAOConfigKey.knownKeys.clear()
          assert(Paideia._daoMap.isEmpty)
          assert(Paideia._actorList.values.flatMap(_.contractInstances.values).isEmpty)
          assert(TotalStakingState._stakingStates.isEmpty)
          assert(new DAOConfigKey(recordedDynamicKeyHash).originalKey.isEmpty)

          val restored = Paideia.restoreState(tmpDir)
          assert(restored.contains(4711), s"lastRestoreError=${Paideia.lastRestoreError}")
          assert(Paideia.lastRestoreError.isEmpty)

          assert(Paideia._daoMap.keySet.toSet == recordedDaoKeys)
          val restoredDao = Paideia.getDAO(dao.key)
          assert(
            Util.bytes2hex(restoredDao.config._config.digest) == recordedConfigDigest
          )

          val restoredInstances =
            Paideia._actorList.values.flatMap(_.contractInstances.values).toList
          assert(restoredInstances.size == recordedInstanceCount)

          // Two Stake instances are live now (the "latest"-version one wired into the
          // config, and the "1.0.0" one built by direct construction), so instances must
          // be told apart by contractHash rather than just isInstanceOf[Stake].
          val restoredStakeInstance = restoredInstances
            .find(i =>
              Util.bytes2hex(i.contractSignature.contractHash.toArray) ==
                recordedStakeHashHex
            )
            .get
          assert(restoredStakeInstance.utxos.toSet == recordedStakeUtxos)
          val restoredUnstakeInstance =
            restoredInstances.find(_.isInstanceOf[Unstake]).get
          assert(restoredUnstakeInstance.utxos.toSet == recordedUnstakeUtxos)

          // The instance the config tree never referenced must come back too, with its
          // box - proving restoreState no longer relies solely on walking the config
          // tree to know which contract instances to recreate.
          val restoredOutdatedStakeInstance = restoredInstances
            .find(i =>
              Util.bytes2hex(i.contractSignature.contractHash.toArray) ==
                recordedOutdatedStakeHashHex
            )
            .get
          assert(restoredOutdatedStakeInstance.isInstanceOf[Stake])
          assert(restoredOutdatedStakeInstance.utxos.toSet == recordedOutdatedStakeUtxos)

          val restoredTss = TotalStakingState(dao.key)
          assert(restoredTss.snapshots.keySet.toSet == recordedSnapshotTimes)
          assert(
            Util.bytes2hex(
              restoredTss.currentStakingState.stakeRecords.digest
            ) == recordedCurrentStakeDigest
          )
          assert(
            Util.bytes2hex(
              restoredTss.currentStakingState.participationRecords.digest
            ) == recordedCurrentParticipationDigest
          )

          assert(restoredDao.proposals(0).name == recordedProposalName)

          // The dynamic key's name must resolve again after restore - both when rebuilt
          // straight from its hashed bytes (exactly how paideia-state's /dao/<key>/config
          // endpoint reconstructs a DAOConfigKey to look its name up) and when reached by
          // iterating the restored config tree itself.
          val restoredDynamicKey = new DAOConfigKey(recordedDynamicKeyHash)
          assert(restoredDynamicKey.originalKey.contains(recordedDynamicKeyName))

          val restoredConfigKeys = restoredDao.config._config.initiate().toMap.keys
          assert(restoredConfigKeys.nonEmpty)
          assert(restoredConfigKeys.forall(_.originalKey.isDefined))

          // Negative: a tampered configDigest must be rejected outright, and leave
          // nothing half-registered.
          Paideia.clearRegistries(closeStores = true)

          val stateFile = new File(tmpDir, "state.json")
          val original  = new String(Files.readAllBytes(stateFile.toPath), "UTF-8")
          assert(original.contains(recordedConfigDigest))
          val tampered = original.replace(recordedConfigDigest, "00" * 32)
          Files.write(stateFile.toPath, tampered.getBytes("UTF-8"))

          val failedRestore = Paideia.restoreState(tmpDir)
          assert(failedRestore.isEmpty)
          assert(Paideia.lastRestoreError.isDefined)
          assert(Paideia._daoMap.isEmpty)
          assert(Paideia._actorList.values.flatMap(_.contractInstances.values).isEmpty)
          assert(TotalStakingState._stakingStates.isEmpty)

          // Negative: a tampered recorded contractHash must also be rejected outright -
          // restoreState recompiles each recorded signature and must not silently accept
          // an instance whose recompiled hash doesn't match what was checkpointed.
          Paideia.clearRegistries(closeStores = true)

          assert(original.contains(recordedStakeHashHex))
          val tamperedHash = original.replace(recordedStakeHashHex, "00" * 32)
          Files.write(stateFile.toPath, tamperedHash.getBytes("UTF-8"))

          val failedHashRestore = Paideia.restoreState(tmpDir)
          assert(failedHashRestore.isEmpty)
          assert(Paideia.lastRestoreError.isDefined)
          assert(Paideia._daoMap.isEmpty)
          assert(Paideia._actorList.values.flatMap(_.contractInstances.values).isEmpty)
          assert(TotalStakingState._stakingStates.isEmpty)
        }
      })
    }
  }

  test("restoreState returns None when state.json doesn't exist") {
    val tmpDir = Files.createTempDirectory("paideia-state-restore-missing").toFile
    assert(Paideia.restoreState(tmpDir).isEmpty)
  }

  test(
    "persistState rewrites a box file that vanished from disk even when its " +
      "fingerprint is unchanged (fullReplay-checkpoint regression)"
  ) {
    // Reproduces the StateLifecycle.fullReplay sequence that produced unrestorable
    // checkpoints in the wild: persist, then the state directory is cleaned while the
    // session (and its box-file fingerprint cache) lives on, then persist again with an
    // UNCHANGED box set. Before the file-exists guard in persistState, the second
    // persist skipped the box file entirely - the fingerprint said it was already on
    // disk - so the checkpoint restored with that contract instance's box set empty,
    // failing chain-state verification fatally on the next start (Config extraOnNode).
    scala.util.Try(Paideia.clearRegistries(closeStores = true))
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]

        val dao           = StakingTest.testDAO
        val stakeContract = Stake(PaideiaContractSignature(daoKey = dao.key))
        stakeContract.newBox(stakeContract.box(ctx, 1000000L).inputBox(), false)
        Paideia.commit()

        val tmpDir = Files.createTempDirectory("paideia-fingerprint").toFile
        Paideia.persistState(tmpDir, 1)

        val sigHashHex =
          Util.bytes2hex(stakeContract.contractSignature.contractHash.toArray)
        val boxFile =
          new File(new File(new File(tmpDir, "boxes"), dao.key), sigHashHex + ".json")
        assert(boxFile.exists(), "first persist must write the box file")

        // What StateLifecycle.discardLocalState does mid-process, minus the replay.
        org.apache.commons.io.FileUtils.cleanDirectory(tmpDir)
        assert(!boxFile.exists())

        Paideia.persistState(tmpDir, 2)
        assert(
          boxFile.exists(),
          "persistState must rewrite a box file that no longer exists on disk, even " +
            "when the box set (and so its fingerprint) is unchanged"
        )
      }
    })
  }
}
