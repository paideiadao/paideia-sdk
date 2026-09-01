package im.paideia.app

import com.typesafe.config.ConfigFactory
import im.paideia.Paideia
import im.paideia.PaideiaSession
import im.paideia.util.PaideiaEnv
import im.paideia.util.Util
import org.apache.commons.io.FileUtils
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files

/** Covers [[GenesisSeeder.seed]] against a hand-built genesis config (the same values as
  * `cli/src/main/resources/genesis/paideia-mainnet.conf` - kept inline here rather than
  * loaded from that resource, since this module has no dependency on the cli module and
  * duplicating a handful of already-public mainnet genesis constants is simpler and just
  * as faithful as reaching across modules for them) rather than a live session's default
  * env, since `seed()` reads `Env.conf` for every genesis value and the sdk's own test
  * `application.conf` (used by `PaideiaSessionFixture`'s default session) carries none of
  * them.
  *
  * Doesn't assert an exact digest value (that would just be pinning today's contract
  * bytecode) - instead asserts what actually matters for a checkpoint's trustworthiness:
  * seeding is deterministic, i.e. two independently seeded fresh sessions, given the same
  * genesis config, produce byte-identical config digests.
  */
class GenesisSeederSuite extends AnyFunSuite {

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
      |  syncStart = 1380365
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

  private val dummyDaoKey =
    "678441d2c6f7254e6b2f317e45989b42ec3dcd33835b4b03b7c61e9fcc80769c"

  private def freshEnv(): PaideiaEnv =
    new PaideiaEnv(
      ConfigFactory.parseString(genesisConfString).resolve().getConfig("paideia")
    )

  /** Runs `body` against a brand-new session (its own temp storeRoot, bound as
    * `Paideia.current` for the duration), cleaning up the session and its temp directory
    * afterwards.
    */
  private def withFreshSession[T](body: PaideiaSession => T): T = {
    val storeRoot = Files.createTempDirectory("genesis-seeder-test-").toFile
    val session   = PaideiaSession(freshEnv(), storeRoot)
    try {
      Paideia.withSession(session)(body(session))
    } finally {
      session.close()
      FileUtils.deleteDirectory(storeRoot)
    }
  }

  test("seed registers the Paideia DAO under its configured key") {
    withFreshSession { session =>
      GenesisSeeder.seed()
      assert(session.daoMap.contains(session.env.paideiaDaoKey))
    }
  }

  test("seed cleans up the dummy DAO it used to instantiate default contracts") {
    withFreshSession { session =>
      GenesisSeeder.seed()
      assert(!session.daoMap.contains(dummyDaoKey))
      assert(
        session.actorList.values
          .flatMap(_.contractInstances.values)
          .forall(_.contractSignature.daoKey != dummyDaoKey)
      )
    }
  }

  test("seed produces a non-empty config digest") {
    withFreshSession { session =>
      GenesisSeeder.seed()
      val digest = Paideia.getConfig(session.env.paideiaDaoKey)._config.digest
      assert(digest.nonEmpty)
    }
  }

  test("seed is deterministic: two fresh sessions produce the same config digest") {
    val digest1 = withFreshSession { session =>
      GenesisSeeder.seed()
      Util.bytes2hex(Paideia.getConfig(session.env.paideiaDaoKey)._config.digest)
    }
    val digest2 = withFreshSession { session =>
      GenesisSeeder.seed()
      Util.bytes2hex(Paideia.getConfig(session.env.paideiaDaoKey)._config.digest)
    }
    assert(digest1.nonEmpty)
    assert(digest1 == digest2)
  }
}
