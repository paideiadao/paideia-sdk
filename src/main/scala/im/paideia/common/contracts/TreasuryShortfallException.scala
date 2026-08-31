package im.paideia.common.contracts

import org.ergoplatform.appkit.InputBoxesSelectionException.NotEnoughErgsException
import org.ergoplatform.appkit.InputBoxesSelectionException.NotEnoughTokensException

/** Thrown by [[Treasury.findBoxes]] when the DAO treasury does not hold enough nanoERG
  * to cover a requested transaction (e.g. a staking emit). Carries the DAO key so
  * callers can attribute the shortfall to a specific DAO instead of treating it as a
  * generic, unattributed appkit exception.
  *
  * @param daoKey
  *   the DAO whose treasury came up short.
  * @param neededNanoErgs
  *   the amount of nanoERG that was required.
  * @param foundNanoErgs
  *   the amount of nanoERG that was actually available in the treasury.
  */
class TreasuryShortfallErgsException(
  val daoKey: String,
  val neededNanoErgs: Long,
  val foundNanoErgs: Long
) extends NotEnoughErgsException(
    f"Not enough erg in treasury to cover ${neededNanoErgs} nanoerg for dao ${daoKey}",
    foundNanoErgs
  )

/** Thrown by [[Treasury.findBoxes]] when the DAO treasury does not hold enough of one
  * or more tokens to cover a requested transaction. Carries the DAO key so callers can
  * attribute the shortfall to a specific DAO instead of treating it as a generic,
  * unattributed appkit exception.
  *
  * @param daoKey
  *   the DAO whose treasury came up short.
  * @param neededTokens
  *   the token amounts that were required, keyed by token id.
  * @param foundTokens
  *   the token amounts that were actually available in the treasury, keyed by token
  *   id.
  */
class TreasuryShortfallTokensException(
  val daoKey: String,
  val neededTokens: Map[String, Long],
  val foundTokens: Map[String, Long]
) extends NotEnoughTokensException(
    f"Not enough tokens founds to cover ${neededTokens} for dao ${daoKey}",
    {
      import scala.collection.JavaConverters._
      foundTokens.map((t: (String, Long)) => (t._1, long2Long(t._2))).asJava
    }
  )
