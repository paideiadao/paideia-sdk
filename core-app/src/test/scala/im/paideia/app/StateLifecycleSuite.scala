package im.paideia.app

import com.google.gson.Gson
import com.typesafe.config.ConfigFactory
import im.paideia.Paideia
import im.paideia.PaideiaSession
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.events.TransactionEvent
import im.paideia.common.sync.BlockSource
import im.paideia.common.sync.VerificationReport
import im.paideia.util.PaideiaEnv
import org.apache.commons.io.FileUtils
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.restapi.client.FullBlock
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.nio.file.Files
import scala.collection.mutable

/** Covers [[StateLifecycle.bringUpToDate]] against a stub [[BlockSource]] (in-memory
  * blocks, same fixture style as `ChainSyncerSuite`) and a stubbed verifier (always-ok,
  * via the injectable `verify` constructor parameter - see there for why: exercising the
  * real `ChainStateVerifier` would need a live indexed node, which is out of scope for
  * this suite, same as `ChainStateVerifierSuite` itself stops at synthetic snapshots
  * rather than a live node).
  *
  * Uses a real, genesis-seeded [[PaideiaSession]] (so `GenesisSeeder.seed()` and
  * `restoreState`/`commit`/`persistState` all run for real) with a
  * [[StubSession.handleEvent]] override only where a test needs to control what a
  * confirmed transaction's event handling returns (the lenient-sink test) - genesis
  * seeding and checkpoint persistence never go through that override.
  */
class StateLifecycleSuite extends AnyFunSuite {

  // A tiny, self-contained genesis config - real key names (GenesisSeeder reads them
  // verbatim from Env.conf) but arbitrary values; syncStart is small so a full replay in
  // these tests only has to cover a handful of blocks.
  private val genesisConfString =
    """
      |paideia {
      |  daoTokenId = "171c56d1aa54a6709bdadcc0f053e7a786411224a8f40111a6878549a3fae842"
      |  paideiaTokenId = "1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad489"
      |  networkType = "mainnet"
      |  paideiaDaoKey = "1b4b8b789fdd4a34c5f1cf73b4d99a5cacb8ccba75265f6edf4950893b162f07"
      |  paideiaOriginNFT = "18b3490e56396577d51c24a1927e635a46887b05826f4e00b130f8193fbdc82a"
      |  operatorAddress = "9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b"
      |  compoundBatchSize = 1000
      |  defaultBotFee = 1000
      |
      |  im_paideia_dao_action_tokenid = "000653ab0e7fb89bfa221d75bd25aed8b98e0bac66a13aa229caf5855128d33a"
      |  im_paideia_dao_proposal_tokenid = "0b2061b664725d7570fdfc40de19b554e60952ced7649f4ad4a9ee2c8640f7c3"
      |  im_paideia_staking_state_tokenid = "233536261ad8920b85644d30fff8e68c470470138950317ad520b300e8c1e573"
      |
      |  syncStart = 1
      |  emission_start = 1729771200000
      |
      |  im_paideia_dao_name = "Paideia"
      |  im_paideia_dao_quorum = 150
      |  im_paideia_dao_threshold = 600
      |  im_paideia_dao_min_proposal_time = 86400000
      |  im_paideia_fees_createdao_erg = 100000000
      |  im_paideia_fees_createdao_paideia = 1000000000
      |  im_paideia_fees_createproposal_paideia = 10000000
      |  im_paideia_fees_compound_operator_paideia = 100
      |  im_paideia_fees_emit_paideia = 20000
      |  im_paideia_fees_emit_operator_paideia = 100
      |  im_paideia_fees_operator_max_erg = 5000000
      |  im_paideia_staking_weight_participation = 10
      |  im_paideia_staking_weight_pureparticipation = 10
      |  im_paideia_staking_cyclelength = 432000000
      |  im_paideia_staking_emission_amount = 273970000
      |  im_paideia_staking_emission_delay = 1
      |  im_paideia_staking_profit_share_pct = 0
      |}
      |""".stripMargin

  private def freshEnv(): PaideiaEnv =
    new PaideiaEnv(
      ConfigFactory.parseString(genesisConfString).resolve().getConfig("paideia")
    )

  private val alwaysOk: BlockchainContextImpl => VerificationReport = _ => VerificationReport(Nil, Nil)

  // ChainSyncer/BlockEvents only ever thread ctx through into TransactionEvent - it's
  // never dereferenced by anything these tests exercise (empty blocks, or a stubbed
  // session.handleEvent that only pattern-matches on the event) - see BlockEventsSuite's
  // and ChainSyncerSuite's identical null stand-in.
  private val ctx: BlockchainContextImpl = null.asInstanceOf[BlockchainContextImpl]

  private def emptyBlock(height: Int): FullBlock = fullBlock(height, Nil)

  private def fullBlock(height: Int, txIds: Seq[String]): FullBlock = {
    val txsJson = txIds
      .map(id => s"""{"id":"$id","inputs":[],"dataInputs":[],"outputs":[]}""")
      .mkString(",")
    val json =
      s"""{
         |  "header": {"id": "header$height", "height": $height},
         |  "blockTransactions": {
         |    "headerId": "header$height",
         |    "transactions": [$txsJson]
         |  }
         |}""".stripMargin
    new Gson().fromJson(json, classOf[FullBlock])
  }

  /** Serves `blockAt`/`bestHeight` from an in-memory map, recording every height
    * requested - used to assert where a replay actually started/stopped.
    */
  private class StubBlockSource(tip: Int, blocks: Map[Int, FullBlock] = Map.empty)
    extends BlockSource {
    val requestedHeights: mutable.Buffer[Int] = mutable.Buffer[Int]()

    override def bestHeight(): Int = tip

    override def blockAt(height: Int): FullBlock = {
      requestedHeights += height
      blocks.getOrElse(height, emptyBlock(height))
    }
  }

  /** A real [[PaideiaSession]] with `handleEvent` swapped out for `handler` - every other
    * method (`restoreState`, `clearRegistries`, `commit`, `persistState`, ...) is the
    * real implementation, so `GenesisSeeder.seed()` and checkpointing under test still
    * exercise real behaviour; only what a confirmed transaction's event handling returns
    * is under the test's control.
    */
  private class StubSession(
    env: PaideiaEnv,
    storeRoot: File,
    handler: PaideiaEvent => PaideiaEventResponse = _ => PaideiaEventResponse(1)
  ) extends PaideiaSession(env, storeRoot) {
    val handledEvents: mutable.Buffer[PaideiaEvent] = mutable.Buffer[PaideiaEvent]()

    override def handleEvent(event: PaideiaEvent): PaideiaEventResponse = {
      handledEvents += event
      handler(event)
    }
  }

  private def withTempSession[T](
    handler: PaideiaEvent => PaideiaEventResponse = _ => PaideiaEventResponse(1)
  )(body: StubSession => T): T = {
    val storeRoot = Files.createTempDirectory("state-lifecycle-test-").toFile
    val session   = new StubSession(freshEnv(), storeRoot, handler)
    try body(session)
    finally {
      session.close()
      FileUtils.deleteDirectory(storeRoot)
    }
  }

  test("no usable checkpoint: seeds genesis and full-replays from syncStart to the tip") {
    withTempSession() { session =>
      val source = new StubBlockSource(tip = 10)
      val lifecycle =
        new StateLifecycle(session, source, indexedClient = null, verify = alwaysOk)

      val height = lifecycle.bringUpToDate(ctx)

      assert(height == 10)
      assert(session.daoMap.contains(session.env.paideiaDaoKey))
      assert(source.requestedHeights.nonEmpty)
      assert(source.requestedHeights.min == 1) // syncStart in genesisConfString
      assert(source.requestedHeights.max == 10)
    }
  }

  test(
    "checkpoints are written every checkpointInterval blocks, and once more at the final block"
  ) {
    val storeRoot        = Files.createTempDirectory("state-lifecycle-test-").toFile
    val persistedHeights = mutable.Buffer[Int]()
    // Spies on PaideiaSession.persistState (called once per checkpoint, with the height
    // being checkpointed) - handleEvent is left as the real implementation here since
    // these blocks carry no transactions.
    val spySession = new PaideiaSession(freshEnv(), storeRoot) {
      override def persistState(dir: File, height: Int): Unit = {
        persistedHeights += height
        super.persistState(dir, height)
      }
    }
    try {
      val source = new StubBlockSource(tip = 10)
      val lifecycle = new StateLifecycle(
        spySession,
        source,
        indexedClient      = null,
        checkpointInterval = 3,
        verify             = alwaysOk
      )

      val height = lifecycle.bringUpToDate(ctx)

      assert(height == 10)
      // Every multiple of 3 in [1, 10] (3, 6, 9), plus one final checkpoint at the last
      // height actually replayed (10) even though it isn't itself a multiple of 3 - see
      // StateLifecycle.replayAndCheckpoint.
      assert(persistedHeights.toList == List(3, 6, 9, 10))
    } finally {
      spySession.close()
      FileUtils.deleteDirectory(storeRoot)
    }
  }

  test(
    "the lenient sink strips exceptions from a benign event-handling failure but logs them, and replay continues"
  ) {
    val logged = mutable.Buffer[String]()
    val handler: PaideiaEvent => PaideiaEventResponse = {
      case te: TransactionEvent if te.tx.getId() == "boom" =>
        PaideiaEventResponse(
          -1,
          exceptions = List(new RuntimeException("synthetic failure"))
        )
      case _ => PaideiaEventResponse(1)
    }

    withTempSession(handler) { session =>
      val blocks = Map(
        1 -> fullBlock(1, Seq("tx1a", "boom", "tx1c"))
      )
      val source = new StubBlockSource(tip = 1, blocks = blocks)
      val lifecycle = new StateLifecycle(
        session,
        source,
        indexedClient = null,
        verify        = alwaysOk,
        log           = logged += _
      )

      // Must not throw - a benign per-event failure (as opposed to a chain-state
      // verification failure) is never fatal to replay.
      val height = lifecycle.bringUpToDate(ctx)

      assert(height == 1)
      assert(
        session.handledEvents.collect { case te: TransactionEvent => te.tx.getId() } ==
          List("tx1a", "boom", "tx1c")
      )
      assert(logged.exists(line => line.contains("synthetic failure")))
    }
  }
}
