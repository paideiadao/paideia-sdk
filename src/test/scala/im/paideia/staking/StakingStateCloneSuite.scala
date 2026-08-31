package im.paideia.staking

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.util.Util
import im.paideia.util.MempoolPlasmaMap
import org.ergoplatform.sdk.ErgoId
import scorex.db.LDBVersionedStore
import scorex.crypto.authds.avltree.batch.VersionedLDBAVLStorage
import sigma.data.AvlTreeFlags
import work.lithos.plasma.PlasmaParameters
import im.paideia.common.PaideiaSessionFixture

/** Covers deliverable 4: StakingState.clone must reproduce the exact source digest via
  * a structure-preserving copy (not FileUtils.copyDirectory, which relied on nothing
  * using LevelDB version history/rollback; and not re-inserting records, since AVL+
  * tree shape depends on operation history), and the result must actually be persisted
  * to disk, not just correct in memory.
  */
class StakingStateCloneSuite extends AnyFunSuite with PaideiaSessionFixture {

  test("clone reproduces the source digest and the clone is genuinely persisted to disk") {
    val daoKey = Util.randomKey
    val state  = StakingState(daoKey, 0L, true)

    // Enough stakers that the tree has real internal structure, not just a lone leaf -
    // a lone-leaf tree would pass even a broken persist step trivially.
    Range(0, 50).foreach(_ =>
      state.stake(Util.randomKey, StakeRecord(100L, 0L, List(0L)), Right(0))
    )

    val originalStakeDigest         = state.stakeRecords.digest
    val originalParticipationDigest = state.participationRecords.digest

    val newEmissionTime = 1000000L
    val clonedState      = state.clone(daoKey, newEmissionTime)

    assert(clonedState.stakeRecords.digest sameElements originalStakeDigest)
    assert(
      clonedState.participationRecords.digest sameElements originalParticipationDigest
    )

    // Prove it's actually on disk: close the clone's stores and reopen fresh
    // MempoolPlasmaMaps directly against the same directories clone() wrote to.
    clonedState.stakeRecords.close()
    clonedState.participationRecords.close()

    val stakeFolder =
      paideiaSession.stakingStateDir(daoKey, "stake", newEmissionTime.toString)
    val participationFolder =
      paideiaSession.stakingStateDir(daoKey, "participation", newEmissionTime.toString)

    val reopenedStake = new MempoolPlasmaMap[ErgoId, StakeRecord](
      new VersionedLDBAVLStorage(new LDBVersionedStore(stakeFolder, 10)),
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )
    reopenedStake.initiate()
    assert(reopenedStake.digest sameElements originalStakeDigest)
    reopenedStake.close()

    val reopenedParticipation = new MempoolPlasmaMap[ErgoId, ParticipationRecord](
      new VersionedLDBAVLStorage(new LDBVersionedStore(participationFolder, 10)),
      AvlTreeFlags.AllOperationsAllowed,
      PlasmaParameters.default
    )
    reopenedParticipation.initiate()
    assert(reopenedParticipation.digest sameElements originalParticipationDigest)
    reopenedParticipation.close()

    state.stakeRecords.close()
    state.participationRecords.close()
  }
}
