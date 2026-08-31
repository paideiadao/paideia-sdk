package im.paideia

import im.paideia.common.PaideiaTestSuite
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.staking.StakeRecord
import im.paideia.staking.StakingTest
import im.paideia.staking.TotalStakingState
import im.paideia.staking.contracts.Stake
import im.paideia.staking.contracts.Unstake
import im.paideia.util.MempoolPlasmaMap
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
  * - so this suite's persist/restore round-trip can never be muddied by another
  * (possibly still-open, since most suites never close their DAOConfig handle) suite's
  * state sharing that same well-known path.
  */
class PaideiaStateRestoreSuite extends PaideiaTestSuite {

  test(
    "persistState + restoreState round-trips registries, digests, boxes, staking " +
      "state and proposals, and is idempotent; a tampered checkpoint is rejected"
  ) {
    // Best-effort: release whatever earlier suites left open process-wide, and start
    // every registry empty, before this test builds and measures its own state.
    scala.util.Try(Paideia.clearRegistries(closeStores = true))

    // MempoolPlasmaMap.live is a single process-wide registry shared with every other
    // suite in this sbt run (see MempoolPlasmaMapPersistenceSuite for the same
    // technique/rationale). Scope it to this test so Paideia.commit()/
    // clearRegistries(closeStores = true) only ever sweep maps this test itself
    // creates, then restore the original registry so nothing else is affected.
    val liveField =
      MempoolPlasmaMap.getClass.getDeclaredField(
        "im$paideia$util$MempoolPlasmaMap$$live"
      )
    liveField.setAccessible(true)
    val originalLive = liveField.get(MempoolPlasmaMap)
    val scopedLive = java.util.Collections.newSetFromMap(
      new java.util.WeakHashMap[MempoolPlasmaMap[_, _], java.lang.Boolean]()
    )
    liveField.set(MempoolPlasmaMap, scopedLive)

    try {
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

          val proposal = dao.newProposal(0, "restore-test-proposal")

          Paideia.commit()

          val tmpDir = Files.createTempDirectory("paideia-state-restore").toFile
          Paideia.persistState(tmpDir, 4711)

          // Record everything persistState should have captured, before anything is
          // cleared, so it can be checked against what restoreState rebuilds.
          val recordedDaoKeys      = Paideia._daoMap.keySet.toSet
          val recordedConfigDigest = Util.bytes2hex(dao.config._config.digest)
          val recordedInstanceCount =
            Paideia._actorList.values.flatMap(_.contractInstances.values).size
          val recordedStakeUtxos      = stakeContract.utxos.toSet
          val recordedUnstakeUtxos = unstakeContract.utxos.toSet
          val recordedSnapshotTimes   = totalStakingState.snapshots.keySet.toSet
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
          // every registry first.
          Paideia.clearRegistries(closeStores = true)
          assert(Paideia._daoMap.isEmpty)
          assert(Paideia._actorList.values.flatMap(_.contractInstances.values).isEmpty)
          assert(TotalStakingState._stakingStates.isEmpty)

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

          val restoredStakeInstance =
            restoredInstances.find(_.isInstanceOf[Stake]).get
          assert(restoredStakeInstance.utxos.toSet == recordedStakeUtxos)
          val restoredUnstakeInstance =
            restoredInstances.find(_.isInstanceOf[Unstake]).get
          assert(restoredUnstakeInstance.utxos.toSet == recordedUnstakeUtxos)

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
        }
      })
    } finally {
      liveField.set(MempoolPlasmaMap, originalLive)
    }
  }

  test("restoreState returns None when state.json doesn't exist") {
    val tmpDir = Files.createTempDirectory("paideia-state-restore-missing").toFile
    assert(Paideia.restoreState(tmpDir).isEmpty)
  }
}
