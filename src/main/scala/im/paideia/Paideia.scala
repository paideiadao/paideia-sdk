package im.paideia

import scala.collection.mutable.HashMap
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.common.contracts.PaideiaActor
import im.paideia.common.events.PaideiaEvent
import im.paideia.common.events.PaideiaEventResponse
import im.paideia.common.filtering.FilterNode
import org.ergoplatform.appkit.InputBox
import im.paideia.governance.contracts.ProposalContract
import java.io.File

/** Facade over `Paideia.current` (a `PaideiaSession`), kept so every existing caller
  * (paideia-state included) compiles and behaves identically without source changes.
  *
  * `Paideia.current` resolves to whatever session was bound with `withSession` for the
  * currently executing thread (via a `DynamicVariable`, so it composes correctly with
  * nested calls and is automatically restored on exit), falling back to
  * `Paideia.default` - a single lazily-created `PaideiaSession()` shared by any code
  * that never opts into an explicit session (i.e. all of today's production code).
  *
  * See PaideiaSession for what used to be here: every `_daoMap`/`_actorList`/
  * `lastRestoreError` field and every `addDAO`/`getDAO`/`initialize`/.../`persistState`/
  * `restoreState`/`clear`/`clearRegistries` method now lives there, operating on one
  * session's own state instead of process-global statics.
  */
object Paideia {
  private val dyn = new scala.util.DynamicVariable[Option[PaideiaSession]](None)

  @volatile private var _default: PaideiaSession = null

  /** The session bound by the innermost enclosing `withSession` call on this thread, or
    * `default` if none is bound.
    */
  def current: PaideiaSession = dyn.value.getOrElse(default)

  /** Lazily creates a single process-wide `PaideiaSession()` (env = PaideiaEnv.load(),
    * storeRoot = new File(".")) the first time it's needed, and returns that same
    * instance on every subsequent call unless replaced via setDefault. This is what
    * every existing caller - never having opted into a session - transparently uses.
    */
  def default: PaideiaSession = {
    if (_default == null) {
      synchronized {
        if (_default == null) _default = PaideiaSession()
      }
    }
    _default
  }

  /** Replaces the fallback session returned by `default`/`current` (when no session is
    * explicitly bound via withSession). Mainly for tests/tools that want a fresh default
    * without relying on DynamicVariable scoping.
    */
  def setDefault(s: PaideiaSession): Unit = _default = s

  /** Runs `body` with `s` bound as `Paideia.current` for the duration of the call (on
    * this thread), restoring whatever was current before on exit - including across
    * nested withSession calls.
    */
  def withSession[T](s: PaideiaSession)(body: => T): T = dyn.withValue(Some(s))(body)

  def _daoMap: HashMap[String, DAO]                     = current.daoMap
  def _actorList: HashMap[String, PaideiaActor]         = current.actorList
  def lastRestoreError: Option[String]                  = current.lastRestoreError

  def clear: Unit = current.clear

  def clearRegistries(closeStores: Boolean): Unit = current.clearRegistries(closeStores)

  def commit(): Int = current.commit()

  def addDAO(dao: DAO): Unit = current.addDAO(dao)

  def getDAO(key: String): DAO = current.getDAO(key)

  def initialize: Unit = current.initialize

  def handleEvent(event: PaideiaEvent): PaideiaEventResponse = current.handleEvent(event)

  def getActor[T <: PaideiaActor](className: String): PaideiaActor =
    current.getActor[T](className)

  def instantiateActor(contractSignature: PaideiaContractSignature) =
    current.instantiateActor(contractSignature)

  def instantiateContractInstance(contractSignature: PaideiaContractSignature) =
    current.instantiateContractInstance(contractSignature)

  def getBox(boxFilter: FilterNode): List[InputBox] = current.getBox(boxFilter)

  def getConfig(daoKey: String): DAOConfig = current.getConfig(daoKey)

  def getProposalContract(contractHash: List[Byte]): ProposalContract =
    current.getProposalContract(contractHash)

  def getBoxById(boxId: String): Option[InputBox] = current.getBoxById(boxId)

  def persistState(dir: File, height: Int): Unit = current.persistState(dir, height)

  def restoreState(dir: File): Option[Int] = current.restoreState(dir)
}
