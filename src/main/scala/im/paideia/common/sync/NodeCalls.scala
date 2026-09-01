package im.paideia.common.sync

/** The retry/backoff loop `NodeBlockSource.nodeCall` runs against a single retrofit node
  * call, extracted so any caller reaching an Ergo node's REST API - not just
  * [[BlockSource]]'s two endpoints - can reuse the exact same behaviour (e.g.
  * [[IndexedNodeClient]]'s indexed-node endpoints).
  *
  * `private[sync]`: this is plumbing shared within the sync package, not part of its
  * public API - callers outside the package go through `NodeBlockSource.nodeCall` (kept
  * public there for paideia-state's non-BlockSource node calls) or a class built on top of
  * [[retry]] (like [[IndexedNodeClient]]).
  */
private[sync] object NodeCalls {

  /** Executes a retrofit node call, retrying on HTTP failure, a null/invalid body, or a
    * thrown exception, up to `maxAttempts` times with exponential backoff (1, 2, 4, 8, 8,
    * ... seconds between attempts). Ported verbatim from `PaideiaSyncTask.nodeCall` (by
    * way of `NodeBlockSource.nodeCall`, which now delegates here).
    *
    * @param maxAttempts
    *   \- maximum number of attempts made for a single node call before giving up.
    * @param onRetry
    *   \- called after every failed attempt (description, attempt number, error message),
    *   before the backoff sleep.
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
  def retry[T](maxAttempts: Int, onRetry: (String, Int, String) => Unit)(
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
