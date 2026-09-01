package im.paideia.cli

import org.scalatest.funsuite.AnyFunSuite

/** Covers the small pure decision points inside [[Main]] that don't need a live
  * node/session - extracted to `private[cli]` methods specifically so they're testable
  * this way (see each method's scaladoc). Everything else in `Main` is orchestration over
  * a real `BlockchainContextImpl`/`PaideiaSession` and isn't covered here.
  */
class MainSuite extends AnyFunSuite {

  // M3
  test("validateVoteTotal accepts an allocation at or under the staked amount") {
    assert(Main.validateVoteTotal(List(50L, 50L), 100L, "dao1") == Right(()))
    assert(Main.validateVoteTotal(List(30L, 20L), 100L, "dao1") == Right(()))
    assert(Main.validateVoteTotal(Nil, 0L, "dao1") == Right(()))
  }

  test("validateVoteTotal rejects an allocation summing to more than the staked amount") {
    val result = Main.validateVoteTotal(List(60L, 60L), 100L, "dao1")
    assert(result.isLeft)
    assert(result.left.get.contains("120"))
    assert(result.left.get.contains("100"))
    assert(result.left.get.contains("dao1"))
  }

  test("validateVoteTotal treats exactly the staked amount as acceptable (not '>')") {
    assert(Main.validateVoteTotal(List(100L), 100L, "dao1") == Right(()))
  }
}
