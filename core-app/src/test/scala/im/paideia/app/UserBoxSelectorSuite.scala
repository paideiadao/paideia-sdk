package im.paideia.app

import im.paideia.common.PaideiaTestSuite
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.util.Util
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.sdk.ErgoToken

/** Offline coverage of [[UserBoxSelector.selectFor]]: the box source is a plain stub
  * function (no `IndexedNodeClient`, no network), and the transaction being funded is a
  * small local [[PaideiaTransaction]] subclass with a fixed protocol `inputs(0)` (exactly
  * the shape every real `StakeTransaction`/`AddStakeTransaction`/`UnstakeTransaction`/
  * `CastVoteTransaction` has - see `im.paideia.app.UserTransactions`'s scaladoc) so
  * `fundsMissing()`'s base `outputBalanceAssets()` (which indexes `inputs(0)`) works
  * unmodified.
  */
class UserBoxSelectorSuite extends PaideiaTestSuite {

  private val walletAddress = "9h7L7sUHZk43VQC3PHtSp5ujAWcZtYmWATBH746wi75C5XHi68b"

  private class FixtureTx(
    _ctx: BlockchainContextImpl,
    _inputs: List[InputBox],
    _outputs: List[OutBox],
    _fee: Long
  ) extends PaideiaTransaction {
    ctx           = _ctx
    inputs        = _inputs
    outputs       = _outputs
    fee           = _fee
    changeAddress = Address.create(walletAddress)
  }

  private def boxWith(
    ctx: BlockchainContextImpl,
    value: Long,
    tokens: List[ErgoToken] = Nil
  ): InputBox = {
    var b = ctx
      .newTxBuilder()
      .outBoxBuilder()
      .contract(Address.create(walletAddress).toErgoContract())
      .value(value)
    if (tokens.nonEmpty) b = b.tokens(tokens: _*)
    b.build().convertToInputWith(Util.randomKey, 0.toShort)
  }

  private def outBoxWith(
    ctx: BlockchainContextImpl,
    value: Long,
    tokens: List[ErgoToken] = Nil
  ): OutBox = {
    var b = ctx
      .newTxBuilder()
      .outBoxBuilder()
      .contract(Address.create(walletAddress).toErgoContract())
      .value(value)
    if (tokens.nonEmpty) b = b.tokens(tokens: _*)
    b.build()
  }

  /** A `FixtureTx` needing `fee + outputValue - protocolInputValue` nanoERG (plus
    * [[UserBoxSelector]]'s own `1,000,000` headroom) and, when `outputTokens` is given,
    * exactly that many tokens - a stand-in for the wallet-funding shortfall every real
    * `UserTransactions` builder leaves behind for `UserBoxSelector` to cover.
    */
  private def fixture(
    ctx: BlockchainContextImpl,
    protocolInputValue: Long,
    outputValue: Long,
    fee: Long,
    outputTokens: List[ErgoToken] = Nil
  ): FixtureTx =
    new FixtureTx(
      ctx,
      List(boxWith(ctx, protocolInputValue)),
      List(outBoxWith(ctx, outputValue, outputTokens)),
      fee
    )

  private def withCtx(body: BlockchainContextImpl => Unit): Unit = {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]
        PaideiaTestSuite.init(ctx)
        body(ctx)
      }
    })
  }

  test("exact cover: a single box exactly meeting the ERG target is selected") {
    withCtx { ctx =>
      // fee 1,000,000 + output 3,500,000 - protocol input 2,000,000 = missing 2,500,000;
      // + 1,000,000 headroom = target 3,500,000.
      val tx = fixture(
        ctx,
        protocolInputValue = 2000000L,
        outputValue        = 3500000L,
        fee                = 1000000L
      )
      val exact = boxWith(ctx, 3500000L)
      val extra = boxWith(ctx, 1000000L)

      val selector = new UserBoxSelector(_ => List(exact, extra))
      val selected = selector.selectFor(tx, List(walletAddress))

      assert(selected == List(exact))
    }
  }

  test(
    "multi-box accumulation: boxes are added until the ERG target is met, no further"
  ) {
    withCtx { ctx =>
      // Same target as above: 3,500,000.
      val tx = fixture(
        ctx,
        protocolInputValue = 2000000L,
        outputValue        = 3500000L,
        fee                = 1000000L
      )
      val b1 = boxWith(ctx, 2000000L)
      val b2 = boxWith(ctx, 2000000L)
      val b3 = boxWith(ctx, 2000000L)

      val selector = new UserBoxSelector(_ => List(b1, b2, b3))
      val selected = selector.selectFor(tx, List(walletAddress))

      // b1 + b2 = 4,000,000 >= 3,500,000 target - b3 is never needed.
      assert(selected == List(b1, b2))
    }
  }

  test(
    "token selection: a box is kept for a needed token even once ERG is already covered"
  ) {
    withCtx { ctx =>
      val daoToken = Util.randomKey
      // fee 1,000,000 + output 1,000,000 (no protocol input contribution) = missing
      // 2,000,000 ERG (target 3,000,000) plus 500 of daoToken.
      val tx = fixture(
        ctx,
        protocolInputValue = 0L,
        outputValue        = 1000000L,
        fee                = 1000000L,
        outputTokens       = List(new ErgoToken(daoToken, 500L))
      )
      val ergOnly   = boxWith(ctx, 3000000L)
      val tokenOnly = boxWith(ctx, 10000L, List(new ErgoToken(daoToken, 500L)))
      val unrelated = boxWith(ctx, 10000L, List(new ErgoToken(Util.randomKey, 999L)))

      val selector = new UserBoxSelector(_ => List(ergOnly, unrelated, tokenOnly))
      val selected = selector.selectFor(tx, List(walletAddress))

      // ergOnly alone satisfies the ERG target; unrelated contributes nothing still
      // needed and must be skipped; tokenOnly is still required for the daoToken amount.
      assert(selected == List(ergOnly, tokenOnly))
    }
  }

  test("insufficient ERG raises a clear error naming what's needed and found") {
    withCtx { ctx =>
      val tx = fixture(
        ctx,
        protocolInputValue = 2000000L,
        outputValue        = 3500000L,
        fee                = 1000000L
      )
      val tooSmall = boxWith(ctx, 1000000L)

      val selector = new UserBoxSelector(_ => List(tooSmall))
      val thrown = intercept[IllegalArgumentException] {
        selector.selectFor(tx, List(walletAddress))
      }
      assert(thrown.getMessage.contains("Insufficient ERG"))
      assert(thrown.getMessage.contains("need 3500000"))
      assert(thrown.getMessage.contains("found 1000000"))
    }
  }

  test("insufficient token raises a clear error naming what's needed and found") {
    withCtx { ctx =>
      val daoToken = Util.randomKey
      val tx = fixture(
        ctx,
        protocolInputValue = 0L,
        outputValue        = 1000000L,
        fee                = 1000000L,
        outputTokens       = List(new ErgoToken(daoToken, 500L))
      )
      val plentyErgSomeToken = boxWith(ctx, 5000000L, List(new ErgoToken(daoToken, 200L)))

      val selector = new UserBoxSelector(_ => List(plentyErgSomeToken))
      val thrown = intercept[IllegalArgumentException] {
        selector.selectFor(tx, List(walletAddress))
      }
      assert(thrown.getMessage.contains("Insufficient token"))
      assert(thrown.getMessage.contains(daoToken))
      assert(thrown.getMessage.contains("need 500"))
      assert(thrown.getMessage.contains("found 200"))
    }
  }

  test("a box contributing nothing still needed is skipped entirely") {
    withCtx { ctx =>
      val tx = fixture(
        ctx,
        protocolInputValue = 2000000L,
        outputValue        = 3500000L,
        fee                = 1000000L
      )
      // Covers the 3,500,000 target by itself, with room to spare.
      val plenty = boxWith(ctx, 5000000L)
      // Once plenty alone has met the ERG target, this box's only asset is a token
      // nothing in the tx needs - it must be skipped, not selected "for the ERG it
      // happens to also carry".
      val unrelated = boxWith(ctx, 100000L, List(new ErgoToken(Util.randomKey, 5L)))

      val selector = new UserBoxSelector(_ => List(plenty, unrelated))
      val selected = selector.selectFor(tx, List(walletAddress))

      assert(selected == List(plenty))
    }
  }

  // --- M2: a box already spent by a not-yet-confirmed mempool transaction must never be
  // picked again - otherwise two transaction commands run back to back (e.g. `stake add`
  // immediately followed by `vote`) can both try to spend the same wallet box, and the
  // second one is stranded (or double-spent) once the first confirms.

  test(
    "M2: a box already spent in the mempool is excluded, even though it would exactly cover the target"
  ) {
    withCtx { ctx =>
      val tx = fixture(
        ctx,
        protocolInputValue = 2000000L,
        outputValue        = 3500000L,
        fee                = 1000000L
      )
      val spentInMempool = boxWith(ctx, 3500000L)
      val stillFree      = boxWith(ctx, 2000000L)
      val alsoFree       = boxWith(ctx, 1500000L)

      val selector = new UserBoxSelector(
        _ => List(spentInMempool, stillFree, alsoFree),
        () => Set(spentInMempool.getId().toString)
      )
      val selected = selector.selectFor(tx, List(walletAddress))

      assert(!selected.contains(spentInMempool))
      assert(selected == List(stillFree, alsoFree))
    }
  }

  test(
    "M2: mempool spending every candidate box surfaces the usual insufficient-ERG error, not a crash"
  ) {
    withCtx { ctx =>
      val tx = fixture(
        ctx,
        protocolInputValue = 2000000L,
        outputValue        = 3500000L,
        fee                = 1000000L
      )
      val onlyBox = boxWith(ctx, 3500000L)

      val selector =
        new UserBoxSelector(_ => List(onlyBox), () => Set(onlyBox.getId().toString))
      val thrown = intercept[IllegalArgumentException] {
        selector.selectFor(tx, List(walletAddress))
      }
      assert(thrown.getMessage.contains("Insufficient ERG"))
      assert(thrown.getMessage.contains("found 0"))
    }
  }

  // --- m8: the per-address box fetch must be memoized for the selector's lifetime, both
  // to avoid a redundant live-node round trip and so two calls within the same CLI
  // invocation (e.g. `stake add`'s auto-detection, then its own funding pass) see a
  // consistent wallet snapshot rather than possibly-different live results.

  test(
    "m8: fetchBoxesByAddress is called at most once per address for the selector's lifetime"
  ) {
    withCtx { ctx =>
      var calls = 0
      val box   = boxWith(ctx, 3500000L)
      val selector = new UserBoxSelector(_ => {
        calls += 1
        List(box)
      })

      selector.unspentBoxes(List(walletAddress))
      selector.unspentBoxes(List(walletAddress))
      val tx = fixture(
        ctx,
        protocolInputValue = 2000000L,
        outputValue        = 3500000L,
        fee                = 1000000L
      )
      selector.selectFor(tx, List(walletAddress))

      assert(calls == 1, s"expected exactly one fetch for $walletAddress, got $calls")
    }
  }
}
