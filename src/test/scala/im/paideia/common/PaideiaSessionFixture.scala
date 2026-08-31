package im.paideia.common

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Outcome
import im.paideia.Paideia
import im.paideia.PaideiaSession
import im.paideia.util.PaideiaEnv
import org.apache.commons.io.FileUtils
import java.nio.file.Files

/** Gives every mixed-in AnyFunSuite its own PaideiaSession, backed by its own temp
  * store root, so suites no longer share (and can't corrupt) the process-global state
  * that used to live in Paideia/PaideiaActor/TotalStakingState/DAOConfigKey/
  * MempoolPlasmaMap - a precondition for running the whole suite with
  * `Test / parallelExecution := true`.
  *
  * One PaideiaSession is created per suite instance (ScalaTest instantiates each Suite
  * class once and runs every one of its tests against that instance), and every test
  * body runs inside `Paideia.withSession(session)` via `withFixture`, so any code that
  * goes through the `Paideia`/`Env`/`TotalStakingState`/`DAOConfigKey`/`MempoolPlasmaMap`
  * facades - which is everything in this codebase - transparently resolves to this
  * suite's own session rather than the shared default one.
  *
  * IMPORTANT: this only isolates code that runs *inside* a test body (or a
  * `beforeAll`/`beforeEach` wrapped in `Paideia.withSession`, see below). Anything a
  * mixing-in suite does at construction time (a top-level `val` touching DAOConfig/
  * Paideia/ConfKeys-derived state, outside any `test(...)` block) runs before
  * `withFixture` ever gets a chance to bind a session, and lands in whatever session
  * happens to be `Paideia.current`/`Paideia.default` at that moment instead - move such
  * code inside a test, or into a `beforeAll` explicitly wrapped in
  * `Paideia.withSession(paideiaSession) { ... }`.
  */
trait PaideiaSessionFixture extends AnyFunSuite with BeforeAndAfterAll {

  private val storeRoot = Files.createTempDirectory("paideia-test-").toFile

  /** This suite's own session - every test body already runs inside
    * `Paideia.withSession(paideiaSession)` (see withFixture), so most tests never need
    * to reference this directly; it's exposed for the rare test that needs the session
    * object itself (e.g. its storeRoot, or to open a second session alongside it).
    */
  protected val paideiaSession: PaideiaSession =
    PaideiaSession(PaideiaEnv.load(), storeRoot)

  override def withFixture(test: NoArgTest): Outcome =
    Paideia.withSession(paideiaSession) {
      super.withFixture(test)
    }

  override def afterAll(): Unit = {
    try {
      paideiaSession.close()
    } finally {
      FileUtils.deleteDirectory(storeRoot)
      super.afterAll()
    }
  }
}
