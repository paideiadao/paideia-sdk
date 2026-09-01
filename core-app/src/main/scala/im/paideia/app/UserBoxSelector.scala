package im.paideia.app

import im.paideia.common.sync.IndexedNodeClient
import im.paideia.common.transactions.PaideiaTransaction
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.InputBoxImpl

import scala.collection.JavaConverters._
import scala.collection.mutable

/** Picks unspent boxes belonging to a set of user addresses to cover a
  * [[im.paideia.common.transactions.PaideiaTransaction]]'s `fundsMissing()` shortfall -
  * the CLI's stand-in for a real wallet's coin selection, used to fund the direct
  * protocol transactions `im.paideia.app.UserTransactions` builds (a first-time stake
  * mints a stake-key NFT; adding to a stake, unstaking, or voting each spend and return
  * the user's own stake-key NFT - see `UserTransactions`'s scaladoc for exactly what each
  * builder needs from the wallet).
  *
  * The box source is injected as a plain function (`fetchBoxesByAddress`) rather than an
  * `IndexedNodeClient` directly, so tests can stub it with in-memory fixture boxes
  * without a live node - see the companion's `apply` for the production wiring over a
  * real `IndexedNodeClient`.
  *
  * @param fetchBoxesByAddress
  *   returns every unspent box currently sitting at one address (a base58 string). The
  *   companion's `apply` wires this to a live node via `IndexedNodeClient`; tests wire it
  *   to a fixed in-memory table instead.
  */
class UserBoxSelector(fetchBoxesByAddress: String => List[InputBox]) {

  /** Every unspent box across every one of `addresses` (duplicate addresses collapsed
    * before fetching; a box present at more than one address isn't possible in practice,
    * so no further de-duplication is done on the result).
    */
  def unspentBoxes(addresses: Seq[String]): List[InputBox] =
    addresses.distinct.flatMap(fetchBoxesByAddress).toList

  /** Greedily selects boxes from `unspentBoxes(addresses)` (in the order that method
    * returns them) to cover `tx.fundsMissing()`: every missing token amount in full, plus
    * enough nanoERG to cover the missing amount and leave `1,000,000` nanoERG of headroom
    * for `tx.unsigned()`'s own change box. A box that would contribute nothing still
    * needed at the time it's considered is skipped rather than selected.
    *
    * Does not mutate `tx` - the caller is expected to assign the result to
    * `tx.userInputs` itself (see `im.paideia.app.UserTransactions`).
    *
    * @throws IllegalArgumentException
    *   if every available box together still doesn't cover the nanoERG or some token
    *   requirement - the message is meant to be shown directly to a CLI user, so it names
    *   the exact shortfall and how many boxes were tried.
    */
  def selectFor(tx: PaideiaTransaction, addresses: Seq[String]): List[InputBox] = {
    val (missingNanoErg, missingTokensRaw) = tx.fundsMissing()
    val targetNanoErg                      = math.max(0L, missingNanoErg) + 1000000L
    val neededTokens: Map[String, Long] = missingTokensRaw.toMap
      .map { case (id, amount) => (id.toString, amount) }
      .filter { case (_, amount) => amount > 0L }

    val available = unspentBoxes(addresses)

    val selected   = List.newBuilder[InputBox]
    var haveErg    = 0L
    val haveTokens = mutable.Map[String, Long]()
    var tried      = 0

    def stillNeedsErg: Boolean = haveErg < targetNanoErg
    def stillNeedsTokens: Boolean =
      neededTokens.exists { case (id, amount) => haveTokens.getOrElse(id, 0L) < amount }

    val it = available.iterator
    while (it.hasNext && (stillNeedsErg || stillNeedsTokens)) {
      val box            = it.next()
      val boxTokens      = box.getTokens().asScala.toList
      val contributesErg = stillNeedsErg && box.getValue() > 0L
      val contributesToken = boxTokens.exists { t =>
        val id = t.id.toString
        neededTokens.get(id).exists(_ > haveTokens.getOrElse(id, 0L))
      }
      if (contributesErg || contributesToken) {
        selected += box
        tried += 1
        haveErg += box.getValue()
        boxTokens.foreach { t =>
          val id = t.id.toString
          haveTokens(id) = haveTokens.getOrElse(id, 0L) + t.value
        }
      }
    }

    if (haveErg < targetNanoErg)
      throw new IllegalArgumentException(
        s"Insufficient ERG: need $targetNanoErg, found $haveErg in $tried boxes"
      )
    neededTokens.foreach { case (id, amount) =>
      val have = haveTokens.getOrElse(id, 0L)
      if (have < amount)
        throw new IllegalArgumentException(
          s"Insufficient token $id: need $amount, found $have in $tried boxes"
        )
    }

    selected.result()
  }
}

object UserBoxSelector {

  /** The hex-encoded ErgoTree an address locks to - the key `IndexedNodeClient`'s
    * ergotree-indexed endpoints are queried by.
    */
  def ergoTreeHex(address: String): String =
    Address.create(address).toErgoContract().getErgoTree().bytesHex

  /** Production wiring: fetches every unspent box at each address's ErgoTree from
    * `indexedClient` and converts the restapi `ErgoTransactionOutput`s to appkit
    * `InputBox`es via `InputBoxImpl` - the same conversion `ChainStateVerifier` uses for
    * live boxes fetched off the same endpoint.
    *
    * @param ctx
    *   only used to sanity-check that every address is on this session's own network
    *   before ever hitting the node - a mainnet/testnet address mix-up otherwise surfaces
    *   much later as a baffling "insufficient funds" from `selectFor`.
    */
  def apply(
    indexedClient: IndexedNodeClient,
    ctx: BlockchainContextImpl
  ): UserBoxSelector =
    new UserBoxSelector((address: String) => {
      val parsed = Address.create(address)
      if (parsed.getNetworkType != ctx.getNetworkType())
        throw new IllegalArgumentException(
          s"address $address is on network ${parsed.getNetworkType}, but this session " +
            s"is on ${ctx.getNetworkType()}"
        )
      indexedClient
        .unspentBoxesByErgoTree(ergoTreeHex(address))
        .map(output => new InputBoxImpl(output))
    })
}
