package im.paideia.common.sync

import org.ergoplatform.restapi.client.ApiClient
import org.ergoplatform.restapi.client.BlockchainApi
import org.ergoplatform.restapi.client.BlockchainIndexHeight
import org.ergoplatform.restapi.client.ErgoTransactionOutput

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/** A thin client over an Ergo node's `blockchain` (address/ergotree-indexed) endpoints,
  * used by [[ChainStateVerifier]] to fetch what's actually unspent on-chain for a given
  * contract's ErgoTree - the ground truth a restored (and therefore untrusted) checkpoint
  * gets checked against once replay has caught it up to the tip. Reuses
  * [[NodeCalls.retry]] for the same retry/backoff behaviour as [[NodeBlockSource]].
  *
  * @constructor
  *   creates a new instance of the IndexedNodeClient class.
  * @param api
  *   \- the retrofit service for the node's blockchain-indexed API.
  * @param maxAttempts
  *   \- maximum number of attempts made for a single node call before giving up.
  * @param onRetry
  *   \- called after every failed attempt (description, attempt number, error message),
  *   before the backoff sleep. Default is a no-op.
  */
class IndexedNodeClient(
  api: BlockchainApi,
  maxAttempts: Int                       = 5,
  onRetry: (String, Int, String) => Unit = (_, _, _) => ()
) {

  private def retry[T](desc: String, valid: T => Boolean = (_: T) => true)(
    call: => retrofit2.Call[T]
  ): T = NodeCalls.retry[T](maxAttempts, onRetry)(desc, valid)(call)

  /** @return
    *   how far the node's blockchain index (the address/ergotree index the `blockchain`
    *   endpoints below are served from) has caught up - which can lag
    *   behind the node's own full chain height. `BlockchainIndexHeight` also carries
    *   `fullHeight` (the node's chain height); `indexedHeight` is the one that bounds
    *   what `unspentBoxesByErgoTree` can actually see.
    */
  def indexHeight(): Int =
    retry[BlockchainIndexHeight]("getBlockchainIndexHeight")(
      api.getBlockchainIndexHeight()
    ).getIndexedHeight().toInt

  /** Pages through every currently-unspent box sitting at `ergoTreeHex`, oldest first,
    * concatenating pages in order until a short page (fewer than `limit` boxes) signals
    * the end.
    *
    * NOTE on parameter order: `BlockchainApi.getUnspentBoxesByErgoTree`'s retrofit
    * signature is `(String, Integer, Integer, String)`, but - confirmed via `javap -v` on
    * ergo-appkit 6.0.1's `BlockchainApi.class` - its `@retrofit2.http.Query` parameter
    * annotations show the *second* Integer is `"limit"` and the *third* is `"offset"`:
    * `getUnspentBoxesByErgoTree(ergoTreeHex, limit, offset, sortDirection)`. This is the
    * OPPOSITE order from the node's own OpenAPI spec, where
    * `/blockchain/box/unspent/byErgoTree` documents `offset` before `limit` in its query
    * string - retrofit's generated call just doesn't preserve that ordering in its Java
    * method signature. Do not swap these back without re-checking the annotations.
    *
    * @param ergoTreeHex
    *   \- the hex-encoded ErgoTree to fetch unspent boxes for.
    * @return
    *   every unspent box at that ErgoTree, oldest first.
    */
  def unspentBoxesByErgoTree(ergoTreeHex: String): List[ErgoTransactionOutput] = {
    val limit                                   = 100
    val boxes                                   = ListBuffer[ErgoTransactionOutput]()
    var offset                                  = 0
    var continue                                = true
    while (continue) {
      val page = retry[java.util.List[ErgoTransactionOutput]](
        s"getUnspentBoxesByErgoTree(offset=$offset)"
      )(
        api.getUnspentBoxesByErgoTree(
          ergoTreeHex,
          limit,
          offset,
          BlockchainApi.sortDirectionOldestFirst
        )
      ).asScala
      boxes ++= page
      offset += page.size
      continue = page.size == limit
    }
    boxes.toList
  }
}

object IndexedNodeClient {

  /** Builds an [[IndexedNodeClient]] talking directly to `nodeUrl`, the same way
    * `NodeBlockSource`'s appkit datasource is normally obtained - a fresh retrofit
    * `ApiClient` bound to that base URL, serving the `BlockchainApi` interface.
    */
  def forNode(nodeUrl: String): IndexedNodeClient =
    new IndexedNodeClient(new ApiClient(nodeUrl).createService(classOf[BlockchainApi]))
}
