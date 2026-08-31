package im.paideia

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.util.PaideiaEnv
import im.paideia.util.ConfKeys
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.contracts.PaideiaContract
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.staking.TotalStakingState
import im.paideia.governance.Proposal
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import scorex.crypto.authds.ADDigest

/** Covers the PaideiaSession/Paideia.withSession contract itself: two sessions never
  * see each other's registries or dynamic key names, a session's store paths land
  * under its own storeRoot, withSession composes/restores correctly (including nested
  * calls), and Paideia.default is what unbound code transparently falls back to.
  *
  * Deliberately does NOT mix in PaideiaSessionFixture: these tests need to observe
  * Paideia.current/Paideia.default themselves (including the "no session bound" case),
  * which the fixture would otherwise always short-circuit by binding a session around
  * every test.
  */
class PaideiaSessionSuite extends AnyFunSuite {

  private def newSession(): PaideiaSession =
    PaideiaSession(PaideiaEnv.load(), Files.createTempDirectory("paideia-session-suite-").toFile)

  private def withTempSession[T](body: PaideiaSession => T): T = {
    val session = newSession()
    try body(session)
    finally {
      session.close()
      FileUtils.deleteDirectory(session.storeRoot)
    }
  }

  /** A PaideiaActor that's never actually asked to build a contract - only used as a
    * key into PaideiaSession.contractInstances(actor).
    */
  private object DummyActor extends PaideiaActor {
    def apply(
      configKey: DAOConfigKey,
      daoKey: String,
      digest: Option[ADDigest] = None
    ): PaideiaContract = ???
    def apply(contractSignature: PaideiaContractSignature): PaideiaContract = ???
  }

  test(
    "two sessions with different temp roots don't see each other's DAOs, contract " +
      "instances, staking states, or dynamic knownKeys"
  ) {
    withTempSession { session1 =>
      withTempSession { session2 =>
        val dynamicKeyHash = Paideia.withSession(session1) {
          Paideia.addDAO(DAO("dao-1", DAOConfig("dao-1")))
          DummyActor.contractInstances.put(List(1.toByte), null)
          TotalStakingState._stakingStates.put("dao-1", null)
          val dynamicKey = DAOConfigKey("im.paideia.test.session-isolation.", Array[Byte](7))
          assert(DAOConfigKey.knownKeys.contains(dynamicKey.hashedKey.toList))
          dynamicKey.hashedKey
        }

        Paideia.withSession(session2) {
          assert(!Paideia._daoMap.contains("dao-1"))
          assert(DummyActor.contractInstances.isEmpty)
          assert(!TotalStakingState._stakingStates.contains("dao-1"))
          assert(!DAOConfigKey.knownKeys.contains(dynamicKeyHash.toList))
          // Rebuilding the same dynamic key from raw bytes must NOT resolve a name in
          // this session - it was never registered here, and it's not one of the
          // static ConfKeys names either.
          assert(new DAOConfigKey(dynamicKeyHash).originalKey.isEmpty)
        }

        // ... and session1 still has everything it registered.
        Paideia.withSession(session1) {
          assert(Paideia._daoMap.contains("dao-1"))
          assert(DummyActor.contractInstances.nonEmpty)
          assert(TotalStakingState._stakingStates.contains("dao-1"))
          assert(new DAOConfigKey(dynamicKeyHash).originalKey.contains(
            "im.paideia.test.session-isolation." + org.ergoplatform.settings.ErgoAlgos
              .encode(Array[Byte](7))
          ))
        }
      }
    }
  }

  test("static ConfKeys names resolve in a freshly created session") {
    // Force ConfKeys' class-init (registers every static name into
    // DAOConfigKey.staticNames, process-wide) - a no-op if some earlier suite in this
    // JVM already touched it, which in practice every other suite does.
    val staticKey = ConfKeys.im_paideia_dao_name
    assert(staticKey.originalKey.contains("im.paideia.dao.name"))

    withTempSession { freshSession =>
      Paideia.withSession(freshSession) {
        // This session's own knownKeys never saw this key - only the process-global
        // staticNames fallback can resolve it here.
        assert(!DAOConfigKey.knownKeys.contains(staticKey.hashedKey.toList))
        val rebuiltFromRawBytes = new DAOConfigKey(staticKey.hashedKey)
        assert(rebuiltFromRawBytes.originalKey.contains("im.paideia.dao.name"))
      }
    }
  }

  test("a session's store directories land under its own storeRoot") {
    withTempSession { session =>
      Paideia.withSession(session) {
        val config = DAOConfig("store-root-dao")
        config.set(ConfKeys.im_paideia_fees_createdao_erg, 1L)
        config._config.commit()
        config._config.close()

        val proposal = Proposal("store-root-dao", 0, "p")
        proposal.votes.close()
      }

      assert(new File(session.storeRoot, "daoconfigs/store-root-dao").isDirectory)
      assert(new File(session.storeRoot, "proposals/store-root-dao/0").isDirectory)
      assert(
        session.daoConfigDir("store-root-dao") ==
          new File(session.storeRoot, "daoconfigs/store-root-dao")
      )
      assert(
        session.stakingStateDir("some-dao", "stake", "current") ==
          new File(session.storeRoot, "stakingStates/some-dao/stake/current")
      )
    }
  }

  test("withSession restores the previously current session on exit, including nested calls") {
    withTempSession { outer =>
      withTempSession { inner =>
        val before = Paideia.current
        Paideia.withSession(outer) {
          assert(Paideia.current eq outer)
          Paideia.withSession(inner) {
            assert(Paideia.current eq inner)
          }
          assert(Paideia.current eq outer)
        }
        assert(Paideia.current eq before)
      }
    }
  }

  test("Paideia.default is used when no session is bound, and is a stable singleton") {
    assert(Paideia.current eq Paideia.default)
    val first  = Paideia.default
    val second = Paideia.default
    assert(first eq second)
  }
}
