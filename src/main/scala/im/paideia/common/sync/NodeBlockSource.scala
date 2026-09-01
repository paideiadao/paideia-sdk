package im.paideia.common.sync

import org.ergoplatform.appkit.impl.NodeAndExplorerDataSourceImpl
import org.ergoplatform.restapi.client.FullBlock
import org.ergoplatform.restapi.client.NodeInfo

/** A [[BlockSource]] backed by a live Ergo node, reached through appkit's
  * `NodeAndExplorerDataSourceImpl`.
  *
  * Ported from paideia-state's `PaideiaSyncTask` (`nodeCall`, `fetchNodeHeight` and
  * `BlockPrefetcher.fetchBlock`) so any process - the sync task itself, a future CLI -
  * can fetch blocks from a node with the same retry/backoff behaviour, without pulling in
  * a Play/akka dependency.
  *
  * @constructor
  *   creates a new instance of the NodeBlockSource class
  * @param datasource
  *   \- the appkit data source used to reach the Ergo node's REST API
  * @param maxAttempts
  *   \- maximum number of attempts made for a single node call before giving up
  * @param onRetry
  *   \- called after every failed attempt (description, attempt number, error message),
  *   before the backoff sleep. Takes the place of a logger so this class has no
  *   logging-framework dependency of its own; the default is a no-op.
  */
class NodeBlockSource(
  datasource: NodeAndExplorerDataSourceImpl,
  maxAttempts: Int                       = 5,
  onRetry: (String, Int, String) => Unit = (_, _, _) => ()
) extends BlockSource {

  /** @return
    *   the node's current full height.
    */
  override def bestHeight(): Int =
    nodeCall[NodeInfo]("getNodeInfo")(
      datasource.getNodeInfoApi().getNodeInfo()
    ).getFullHeight()

  /** Fetches the full block at `height` in two steps, mirroring
    * `BlockPrefetcher.fetchBlock`: first resolve the header id of the (single, since Ergo
    * has no forks past finality) block at that height, then fetch the full block by that
    * header id.
    *
    * @param height
    *   the height of the block to fetch.
    * @return
    *   the full block (header + transactions) at `height`.
    */
  override def blockAt(height: Int): FullBlock = {
    val headerId = nodeCall(
      s"getFullBlockAt($height)",
      valid = (l: java.util.List[String]) => !l.isEmpty
    )(
      datasource.getNodeBlocksApi().getFullBlockAt(height)
    ).get(0)
    nodeCall[FullBlock]("getFullBlockById")(
      datasource.getNodeBlocksApi().getFullBlockById(headerId)
    )
  }

  /** Executes a retrofit node call, retrying on HTTP failure, a null/invalid body, or a
    * thrown exception, up to `maxAttempts` times with exponential backoff (1, 2, 4, 8, 8,
    * ... seconds between attempts). Ported verbatim from `PaideiaSyncTask.nodeCall`, with
    * the state actor's `logger.warn` replaced by the `onRetry` callback.
    *
    * Package-private so the retry behaviour can be exercised directly from tests with
    * hand-rolled `retrofit2.Call` stubs, without going through a real datasource.
    *
    * @param desc
    *   \- a short description of the call, used in retry notifications and the final
    *   failure message.
    * @param valid
    *   \- an extra validity check run on a successful response's body; a body that fails
    *   it is treated the same as an HTTP failure and retried.
    * @param call
    *   \- produces the retrofit `Call` to execute; re-evaluated on every attempt.
    * @return
    *   the response body, once a call succeeds.
    * @throws RuntimeException
    *   if every attempt fails; the last error is included in the message, and the last
    *   thrown exception (if any) is preserved as the cause.
    */
  private[sync] def nodeCall[T](
    desc: String,
    valid: T => Boolean = (_: T) => true
  )(call: => retrofit2.Call[T]): T = {
    var attempt                          = 1
    var lastException: Option[Exception] = None
    var lastErrorMessage: String         = "unknown error"
    while (attempt <= maxAttempts) {
      try {
        val resp = call.execute()
        if (resp.isSuccessful() && resp.body() != null && valid(resp.body())) {
          return resp.body()
        } else {
          lastException = None
          lastErrorMessage =
            if (!resp.isSuccessful())
              s"HTTP ${resp.code()}: ${resp.message()}"
            else if (resp.body() == null)
              s"HTTP ${resp.code()}: empty body"
            else
              s"HTTP ${resp.code()}: invalid body"
        }
      } catch {
        case e: Exception =>
          lastException    = Some(e)
          lastErrorMessage = e.getMessage()
      }
      onRetry(desc, attempt, lastErrorMessage)
      if (attempt < maxAttempts) {
        val backoffSeconds = math.min(8, math.pow(2, attempt - 1).toInt)
        Thread.sleep(backoffSeconds * 1000L)
      }
      attempt += 1
    }
    lastException match {
      case Some(e) =>
        throw new RuntimeException(
          s"Node call failed after $maxAttempts attempts: $desc: $lastErrorMessage",
          e
        )
      case None =>
        throw new RuntimeException(
          s"Node call failed after $maxAttempts attempts: $desc: $lastErrorMessage"
        )
    }
  }
}
