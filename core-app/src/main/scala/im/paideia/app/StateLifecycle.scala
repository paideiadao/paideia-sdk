package im.paideia.app

import im.paideia.Paideia
import im.paideia.PaideiaSession
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.sync.BlockSource
import im.paideia.common.sync.ChainStateVerifier
import im.paideia.common.sync.ChainSyncer
import im.paideia.common.sync.IndexedNodeClient
import im.paideia.common.sync.VerificationReport
import im.paideia.util.Env
import org.apache.commons.io.FileUtils
import org.ergoplatform.appkit.impl.BlockchainContextImpl

import java.io.File

/** The framework-free state lifecycle described in `PHASE4-CLI-DESIGN.md`'s "State
  * lifecycle" section: bring a session's local state up to date with the chain tip -
  * resuming from a checkpoint when one is usable, falling all the way back to a trustless
  * full replay from the baked-in genesis conf when it isn't - and verify the result
  * against on-chain digests before it's trusted for anything else.
  *
  * Synchronous, single-threaded, no Play/akka dependency: everything that in
  * paideia-state is spread across `PaideiaStateActor.initializeState`/`seedGenesis`,
  * `PaideiaSyncTask.syncRemainingBlocks`'s checkpoint policy, and
  * `PaideiaStateActor.commitBlock` is one call here - [[bringUpToDate]] - built out of
  * the W1/W2 primitives ([[BlockSource]], [[ChainSyncer]], [[ChainStateVerifier]]) plus
  * [[GenesisSeeder]].
  *
  * @constructor
  *   creates a new instance of the StateLifecycle class.
  * @param session
  *   the session whose state is being brought up to date; every mutation this class
  *   performs runs with `session` bound as `Paideia.current` (see [[bound]]) - callers
  *   don't need to `Paideia.setDefault(session)` first, though doing so is still needed
  *   for any Paideia.*-facade code the caller itself runs outside of a `StateLifecycle`
  *   call (e.g. `im.paideia.app.ReadModels` queries after `bringUpToDate` returns).
  * @param blockSource
  *   where confirmed blocks and the chain height come from - a live node in production,
  *   an in-memory stub in tests.
  * @param indexedClient
  *   reaches the node's indexed `blockchain` endpoints for [[ChainStateVerifier.verify]].
  * @param checkpointInterval
  *   how often (in blocks) to checkpoint (`session.commit()` + `session.persistState`)
  *   while replaying; the final block of every replay range is always checkpointed too,
  *   regardless of this value - see [[replayAndCheckpoint]].
  * @param log
  *   progress/log callback (e.g. a stderr printer for a CLI); defaults to a no-op.
  * @param verify
  *   produces the [[VerificationReport]] used to decide whether the session's state, once
  *   caught up, actually matches on-chain reality. Defaults to
  *   `ChainStateVerifier.verify(indexedClient, ctx)`; overridable so tests can stub
  *   verification without a live node/index (see `StateLifecycleSuite`).
  */
class StateLifecycle(
  session: PaideiaSession,
  blockSource: BlockSource,
  indexedClient: IndexedNodeClient,
  checkpointInterval: Int          = 100,
  log: String => Unit              = _ => (),
  verify: BlockchainContextImpl => VerificationReport = null
) {

  require(checkpointInterval > 0, "checkpointInterval must be positive")

  /** `<storeRoot>/state`, mirroring paideia-state's `stateDir` (there, `Env.conf`'s
    * `stateDir` key, resolved relative to the process CWD; here, always under this
    * session's own `storeRoot` so two sessions in one JVM never collide).
    */
  val stateDir: File = new File(session.storeRoot, "state")

  /** Resolves [[verify]]'s effective behaviour: the constructor parameter when given, or
    * `ChainStateVerifier.verify(indexedClient, ctx)` otherwise. A plain default expression on
    * the `verify` parameter itself can't reference `indexedClient` (a class constructor's
    * default arguments can only refer to earlier PARAMETER LIST groups, not other
    * parameters of the same list - unlike an ordinary method's defaults), so the
    * `indexedClient`-dependent default is resolved here instead, in the class body, where
    * `indexedClient` is normally in scope.
    */
  private def verifyNow(ctx: BlockchainContextImpl): VerificationReport =
    (if (verify == null)
       (c: BlockchainContextImpl) => ChainStateVerifier.verify(indexedClient, c)
     else verify)(ctx)

  /** Runs `body` with `session` bound as `Paideia.current`, the same pattern
    * `PaideiaSession.bound` uses internally for its own methods - needed here because
    * [[GenesisSeeder.seed]] and [[ChainStateVerifier.verify]] resolve the session only
    * through the `Paideia.current`/`Paideia._actorList`/`Paideia._daoMap` facades, unlike
    * `PaideiaSession`'s own methods (`restoreState`, `commit`, `persistState`,
    * `handleEvent`, ...), which are already self-bound and safe to call regardless of
    * what happens to be `Paideia.current`.
    */
  private def bound[T](body: => T): T = Paideia.withSession(session)(body)

  /** Feeds an event to `session.handleEvent` (already self-bound, see [[bound]]) and, on
    * any exception, logs it and strips it from the response rather than propagating it -
    * unlike `ChainSyncer`'s default strict behaviour (see `ChainSyncer.replay`'s
    * scaladoc), replay here must not die on a benign bot-tx-generation failure (e.g. a
    * treasury shortfall CreateTransactionsEvent can't satisfy); [[ChainStateVerifier]] is
    * the actual safety net that catches a replay that went wrong for a real reason.
    */
  private def lenientSink(event: PaideiaEvent): PaideiaEventResponse = {
    val response = session.handleEvent(event)
    response.exceptions.foreach { e =>
      log(s"[sync] event handling raised (ignored, continuing replay): ${e.getMessage}")
    }
    response.copy(exceptions = Nil)
  }

  private def syncer: ChainSyncer = new ChainSyncer(blockSource, lenientSink)

  private def onCheckpointInterval(height: Int): Unit =
    if (height % checkpointInterval == 0) {
      session.commit()
      session.persistState(stateDir, height)
    }

  /** Replays from `fromHeight` to the chain tip, checkpointing every `checkpointInterval`
    * blocks (via [[onCheckpointInterval]]) and unconditionally once more after the replay
    * loop finishes - covering the "final block" half of the checkpoint policy regardless
    * of whether the tip (which `ChainSyncer.replayToTip` re-reads as it approaches it,
    * see there) happened to land on a `checkpointInterval` boundary.
    *
    * @return
    *   the last height actually processed; `fromHeight - 1` if nothing needed replaying
    *   (already caught up) - in which case no extra checkpoint is written, since nothing
    *   changed.
    */
  private def replayAndCheckpoint(ctx: BlockchainContextImpl, fromHeight: Int): Int = {
    val last = syncer.replayToTip(ctx, fromHeight, onBlock = onCheckpointInterval)
    if (last >= fromHeight) {
      session.commit()
      session.persistState(stateDir, last)
    }
    last
  }

  /** Wipes this session's on-disk state to CONTENTS only (`stateDir`, `daoconfigs`,
    * `proposals`, `stakingStates` under `session.storeRoot`) - mirrors
    * `PaideiaStateActor.initializeState`'s failed-restore cleanup, which uses
    * `FileUtils.cleanDirectory` rather than deleting the directories themselves so a
    * bind-mounted directory (as in paideia-state's docker deployment) doesn't fail with
    * EBUSY. Caller must already have called `session.clearRegistries(closeStores = true)`
    * first (see [[fullReplay]]) so no store handle is still open on these directories
    * when they're cleaned.
    */
  private def discardLocalState(): Unit = {
    Seq(
      stateDir,
      new File(session.storeRoot, "daoconfigs"),
      new File(session.storeRoot, "proposals"),
      new File(session.storeRoot, "stakingStates")
    )
      .filter(_.isDirectory)
      .foreach(FileUtils.cleanDirectory)
  }

  /** The trustless fallback path: discard whatever local state exists, re-seed genesis,
    * and replay the whole archive from `syncStart` (`Env.conf`, i.e. this session's
    * `paideia.syncStart` - the height just before this protocol instance's bootstrap
    * transactions landed) to the chain tip. Used both when there's no usable checkpoint
    * to restore from, and as the last-resort recovery when a restored checkpoint keeps
    * failing verification (see [[bringUpToDate]]).
    */
  private def fullReplay(ctx: BlockchainContextImpl): Int = {
    session.clearRegistries(closeStores = true)
    discardLocalState()
    bound(GenesisSeeder.seed())
    val syncStart = Env.conf.getInt("syncStart")
    replayAndCheckpoint(ctx, syncStart)
  }

  /** Verifies the session's current state at `height` against on-chain reality, retrying
    * up to 3 total attempts (a fresh gap-replay-to-tip between each, since the chain may
    * have advanced while verifying/replaying - see `ChainStateVerifier`'s caveat) before
    * giving up on this phase.
    *
    * @param wasRestored
    *   whether `height` was reached via a restored checkpoint (`true`) or a full replay
    *   (`false`) - decides what happens once every attempt in this phase has failed: a
    *   restored checkpoint that can't be verified is discarded for a single one-shot full
    *   replay, whose own verify gets a fresh retry phase with `wasRestored = false` (a
    *   full replay that still doesn't verify is a genuine failure, no further fallback);
    *   a full replay that can't be verified is fatal.
    * @throws IllegalStateException
    *   if verification never succeeds - after the one-shot full-replay fallback when
    *   `wasRestored`, or immediately otherwise.
    */
  private def verifyCatchingUp(
    ctx: BlockchainContextImpl,
    height: Int,
    wasRestored: Boolean,
    attempt: Int = 1
  ): Int = {
    val report = verifyNow(ctx)
    if (report.ok) {
      // Surfaces the report on success too - it's the only place the verifier's
      // non-fatal [boxes][warn] lines (untracked on-chain boxes on contracts where
      // extras aren't enforced) ever reach the caller's log.
      log(s"[sync] verification: ${report.describe}")
      height
    } else if (attempt < 3) {
      log(
        s"[sync] chain-state verification failed (attempt $attempt/3), re-replaying to tip: " +
          report.describe
      )
      val last = replayAndCheckpoint(ctx, height + 1)
      verifyCatchingUp(ctx, math.max(height, last), wasRestored, attempt + 1)
    } else if (wasRestored) {
      log(
        "[sync] chain-state verification still failing after 3 attempts on a restored " +
          "checkpoint; discarding local state and falling back to a full replay: " +
          report.describe
      )
      // Recurse as the full-replay case so the fallback's own verify gets the same
      // transient-mismatch retry allowance (the chain keeps advancing during a full
      // replay too); wasRestored = false makes a still-failing verify fatal, so this
      // fallback runs at most once.
      verifyCatchingUp(ctx, fullReplay(ctx), wasRestored = false)
    } else {
      throw new IllegalStateException(
        "StateLifecycle.bringUpToDate: chain-state verification failed after a full " +
          "replay from genesis: " + report.describe
      )
    }
  }

  /** Brings `session`'s state up to date with the chain tip and verifies it, per the
    * design doc's state-lifecycle diagram:
    *
    *   1. `session.restoreState(stateDir)`: `Some(h)` gap-replays from `h + 1` to the
    *      tip; `None` discards whatever's there (see [[fullReplay]]) and full-replays
    *      from `syncStart` instead.
    *   1. Either way, replay uses [[lenientSink]] - a benign bot-tx-generation failure
    *      (e.g. a treasury shortfall) must not abort replay; [[ChainStateVerifier]] is
    *      the actual correctness check.
    *   1. Checkpoints ([[replayAndCheckpoint]]) are written periodically and always at
    *      the end of a replay range.
    *   1. The result is verified against on-chain digests ([[verifyCatchingUp]]), with
    *      retries and a one-shot full-replay fallback for a restored checkpoint that
    *      won't verify.
    *
    * @return
    *   the height this session's state is caught up to and verified at.
    * @throws IllegalStateException
    *   if verification never succeeds, per [[verifyCatchingUp]].
    */
  def bringUpToDate(ctx: BlockchainContextImpl): Int = bound {
    val (height, wasRestored) = session.restoreState(stateDir) match {
      case Some(h) =>
        log(s"[sync] restored checkpoint at height $h, gap-replaying to tip")
        val last = replayAndCheckpoint(ctx, h + 1)
        (math.max(h, last), true)
      case None =>
        log(
          "[sync] no usable checkpoint (" +
            session.lastRestoreError.getOrElse("no checkpoint found") +
            "), falling back to a full replay from genesis"
        )
        (fullReplay(ctx), false)
    }
    verifyCatchingUp(ctx, height, wasRestored)
  }
}
