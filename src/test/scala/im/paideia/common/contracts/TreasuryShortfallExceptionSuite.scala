package im.paideia.common.contracts

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.common.PaideiaSessionFixture
import org.ergoplatform.appkit.InputBoxesSelectionException.NotEnoughErgsException
import org.ergoplatform.appkit.InputBoxesSelectionException.NotEnoughTokensException
import org.ergoplatform.appkit.InputBoxesSelectionException

class TreasuryShortfallExceptionSuite extends AnyFunSuite with PaideiaSessionFixture {

  test("TreasuryShortfallErgsException carries dao key and needed/found nanoErgs") {
    val ex = new TreasuryShortfallErgsException("dummy.dao.key", 1000L, 500L)
    assert(ex.daoKey === "dummy.dao.key")
    assert(ex.neededNanoErgs === 1000L)
    assert(ex.foundNanoErgs === 500L)
    assert(ex.balanceFound === 500L)
    assert(ex.isInstanceOf[NotEnoughErgsException])
    assert(ex.isInstanceOf[InputBoxesSelectionException])
    assert(ex.getMessage.contains("dummy.dao.key"))
    assert(ex.getMessage.contains("1000"))
  }

  test("TreasuryShortfallTokensException carries dao key and needed/found token maps") {
    val needed = Map("tokenA" -> 100L, "tokenB" -> 50L)
    val found  = Map("tokenA" -> 30L)
    val ex     = new TreasuryShortfallTokensException("dummy.dao.key", needed, found)
    assert(ex.daoKey === "dummy.dao.key")
    assert(ex.neededTokens === needed)
    assert(ex.foundTokens === found)
    assert(ex.isInstanceOf[NotEnoughTokensException])
    assert(ex.isInstanceOf[InputBoxesSelectionException])
    assert(ex.getMessage.contains("dummy.dao.key"))
    // tokenBalances is the appkit-visible java.util.Map view of foundTokens
    assert(ex.tokenBalances.get("tokenA") === 30L)
    assert(ex.tokenBalances.size() === found.size)
  }

  test("TreasuryShortfallTokensException with empty found tokens") {
    val needed = Map("tokenA" -> 100L)
    val found  = Map.empty[String, Long]
    val ex     = new TreasuryShortfallTokensException("dummy.dao.key", needed, found)
    assert(ex.foundTokens.isEmpty)
    assert(ex.tokenBalances.isEmpty)
  }
}
