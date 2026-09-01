package im.paideia.cli

import com.typesafe.config.ConfigFactory
import im.paideia.Paideia
import im.paideia.PaideiaSession
import im.paideia.app.GenesisSeeder
import im.paideia.util.PaideiaEnv
import im.paideia.util.Util
import org.apache.commons.io.FileUtils
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files

/** Pins the mainnet genesis config tree digest produced by seeding from the CLI's
  * baked-in `cli-defaults.conf` (which includes `genesis/paideia-mainnet.conf`, a
  * byte-for-byte copy of paideia-state's genesis seed).
  *
  * This digest is a protocol constant, not an implementation detail: the seed must
  * reproduce the digest of the historic on-chain genesis config box byte-for-byte, or
  * replay from genesis silently forks from mainnet. It depends on the genesis values AND
  * on the compiled contract bytecode (default contract ErgoTrees and signatures are
  * config values), so a contract change that would break all mainnet replay fails here
  * instead of in production. If this test fails, do NOT update the constant unless the
  * protocol instance itself changed - find out what changed the seed.
  */
class GenesisDigestSuite extends AnyFunSuite {

  /** The digest of `paideiaConfig._config` right after `GenesisSeeder.seed()` on the
    * mainnet genesis conf, hex-encoded via `Util.bytes2hex` (33 bytes: 32-byte root hash
    * + 1-byte tree height, the AVL digest format).
    */
  private val mainnetGenesisConfigDigestHex =
    "1375a5d0ed0e759d91187d3743d18db0c8298099cefabeb65d23a199de933bea07"

  test("seeding from the baked-in mainnet genesis conf reproduces the pinned digest") {
    val conf =
      ConfigFactory.parseResources("cli-defaults.conf").resolve().getConfig("paideia")
    val storeRoot = Files.createTempDirectory("genesis-digest-test-").toFile
    val session   = PaideiaSession(new PaideiaEnv(conf), storeRoot)
    try {
      Paideia.withSession(session) {
        GenesisSeeder.seed()
        val digest =
          Util.bytes2hex(Paideia.getConfig(session.env.paideiaDaoKey)._config.digest)
        assert(
          digest == mainnetGenesisConfigDigestHex,
          s"genesis seed digest changed: got $digest - this breaks replay from genesis " +
            "against mainnet unless the protocol instance itself changed"
        )
      }
    } finally {
      session.close()
      FileUtils.deleteDirectory(storeRoot)
    }
  }
}
