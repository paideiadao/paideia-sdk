package im.paideia.app

import im.paideia.Paideia
import im.paideia.common.transactions.PaideiaTransaction
import im.paideia.governance.VoteRecord
import im.paideia.governance.transactions.CastVoteTransaction
import im.paideia.staking.StakeRecord
import im.paideia.staking.transactions.AddStakeTransaction
import im.paideia.staking.transactions.StakeTransaction
import im.paideia.staking.transactions.UnstakeTransaction
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import sigma.Colls

/** Builders for the four DIRECT protocol transactions W4a's CLI transaction commands
  * submit - `stake add`/`stake remove`/`vote` (and `stake add`'s first-time-stake case).
  *
  * These are NOT a proxy-box design: every transaction here is the exact same
  * `PaideiaTransaction` subclass paideia-app's own frontend builds and paideia-state's
  * `PaideiaStateActor.stakeTransaction`/`addStakeTransaction`/`unstakeTransaction`/
  * `castVoteTransaction` construct for a browser wallet today (see those methods'
  * scaladoc references) - `StakeTransaction`, `AddStakeTransaction`,
  * `UnstakeTransaction`, `CastVoteTransaction`. Each already reads the live protocol
  * state itself (the current `StakeState`/`ChangeStake`/`Unstake`/`StakeVote`/proposal
  * boxes) and fills in its own `inputs`/`dataInputs`/`outputs`/`fee` - the only thing
  * left for a caller to supply is `userInputs`: the wallet UTXOs that fund the
  * transaction and (for `addStake`/`unstake`/ `vote`) carry the user's own stake-key NFT
  * the transaction spends and returns. That's exactly [[UserBoxSelector]]'s job, driven
  * off each transaction's own `fundsMissing()`
  *   - see `fund` below.
  *
  * Every builder here takes an already-built [[UserBoxSelector]] rather than an
  * `IndexedNodeClient` directly, so callers share one selector (and therefore one set of
  * fetched wallet boxes) across a whole CLI invocation, and so tests can inject a
  * selector backed by fixture boxes instead of a live node - see `UserTransactionsSuite`.
  *
  * Building the same transaction two independent CLI users happen to pick at once will
  * contend on the same protocol input boxes (the `StakeState`/`ChangeStake`/`Unstake`/
  * `StakeVote` singleton boxes) - whichever's tx confirms first wins, and the loser's tx
  * simply fails to confirm. That's an accepted, low-frequency-protocol tradeoff (the same
  * one paideia-app's own frontend already lives with), not a bug in this CLI.
  */
object UserTransactions {

  /** Fills `tx.userInputs` from `addresses`' own unspent boxes via `selector`, driven by
    * `tx.fundsMissing()` - see there for exactly what "funds" means (nanoERG headroom
    * plus every token the transaction's own outputs need that its protocol `inputs` don't
    * already supply, e.g. the stake-key NFT `addStake`/`unstake`/`vote` return to the
    * user).
    *
    * Also sets `tx.minimizeChangeBox = false` - CRITICAL: `PaideiaTransaction.unsigned()`
    * defaults `minimizeChangeBox = true`, and in that mode it folds the ENTIRE change
    * surplus above a dust-sized box into `fee` rather than returning it to the user (see
    * `PaideiaTransaction.scala`'s change-box branch). That default is fine for a protocol
    * bot funding itself from a tightly-sized treasury box, but the CLI funds from
    * whatever wallet box `UserBoxSelector` happens to pick - typically far larger than
    * what's needed - so leaving it on would silently charge the user their entire
    * leftover balance as a "fee". paideia-state's own `MUnsignedTransaction.apply`
    * (`app/models/MUnsignedTransaction.scala:63`) sets this to `false` before funding for
    * exactly this reason; every CLI transaction must too.
    */
  private def fund(
    tx: PaideiaTransaction,
    selector: UserBoxSelector,
    addresses: Seq[String]
  ): PaideiaTransaction = {
    tx.minimizeChangeBox = false
    tx.userInputs        = selector.selectFor(tx, addresses)
    tx
  }

  /** A first-time stake: mints a new stake-key NFT (id = the `StakeState` box's own id at
    * the moment it's spent) straight to `addresses.head`, per `StakeTransaction`.
    */
  def stake(
    ctx: BlockchainContextImpl,
    selector: UserBoxSelector,
    daoKey: String,
    amount: Long,
    addresses: Seq[String],
    changeAddress: String
  ): PaideiaTransaction = {
    val userAddress = Address.create(addresses.head)
    val tx =
      StakeTransaction(ctx, amount, userAddress, Address.create(changeAddress), daoKey)
    fund(tx, selector, addresses)
  }

  /** Adds to an existing stake identified by `stakeKey` - the user must already hold that
    * stake key's NFT in one of `addresses`' wallets; `selector` finds and spends it
    * (returned right back to `addresses.head` by `AddStakeTransaction` itself).
    */
  def addStake(
    ctx: BlockchainContextImpl,
    selector: UserBoxSelector,
    daoKey: String,
    stakeKey: String,
    amount: Long,
    addresses: Seq[String],
    changeAddress: String
  ): PaideiaTransaction = {
    val userAddress = Address.create(addresses.head)
    val tx = AddStakeTransaction(
      ctx,
      amount,
      stakeKey,
      Address.create(changeAddress),
      userAddress,
      daoKey,
      null
    )
    fund(tx, selector, addresses)
  }

  /** Withdraws (fully or partially) an existing stake identified by `stakeKey`, per
    * `UnstakeTransaction`. `newStakeRecord` is the record the transaction writes back
    * (`ChangeStake`), or - when its `stake` is `0` - the trigger for `Unstake` to delete
    * the record and burn the stake-key NFT entirely instead. Same NFT-ownership
    * requirement as [[addStake]].
    */
  def unstake(
    ctx: BlockchainContextImpl,
    selector: UserBoxSelector,
    daoKey: String,
    stakeKey: String,
    newStakeRecord: StakeRecord,
    addresses: Seq[String],
    changeAddress: String
  ): PaideiaTransaction = {
    val userAddress = Address.create(addresses.head)
    val tx = UnstakeTransaction(
      ctx,
      stakeKey,
      Colls.fromArray(newStakeRecord.toBytes),
      Address.create(changeAddress),
      userAddress,
      daoKey,
      null
    )
    fund(tx, selector, addresses)
  }

  /** Casts (or replaces) a vote on `proposalIndex`, weighted per option by `votes`, per
    * `CastVoteTransaction`. Same NFT-ownership requirement as [[addStake]]/[[unstake]] -
    * casting a vote spends and returns the voter's own stake-key NFT.
    */
  def vote(
    ctx: BlockchainContextImpl,
    selector: UserBoxSelector,
    daoKey: String,
    stakeKey: String,
    proposalIndex: Int,
    votes: Array[Long],
    addresses: Seq[String],
    changeAddress: String
  ): PaideiaTransaction = {
    val userAddress = Address.create(addresses.head)
    val tx = CastVoteTransaction(
      ctx,
      proposalIndex,
      stakeKey,
      VoteRecord(votes),
      Paideia.getDAO(daoKey),
      Address.create(changeAddress),
      userAddress
    )
    fund(tx, selector, addresses)
  }
}
