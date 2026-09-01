package im.paideia.common.sync

import java.util.concurrent.atomic.AtomicInteger

/** A hand-rolled `retrofit2.Call` stub for exercising `NodeBlockSource.nodeCall`'s retry
  * behaviour without a real HTTP round-trip.
  *
  * Each call to `execute()` runs the next behaviour in `behaviours`, in order; once
  * exhausted, the last behaviour repeats. A behaviour is free to return a
  * `retrofit2.Response` or throw, letting a single stub script both HTTP-level failures
  * (an unsuccessful/invalid response) and transport-level failures (a thrown exception)
  * across successive attempts.
  *
  * `call` in `NodeBlockSource.nodeCall` is by-name, so passing a single `StubCall`
  * instance (as opposed to constructing a fresh one inline) is what makes `executedCount`
  * accumulate across retries within one `nodeCall` invocation.
  *
  * @param behaviours
  *   \- one function per attempt, in order; the last is reused for any attempt beyond the
  *   list's length.
  */
class StubCall[T](behaviours: Seq[() => retrofit2.Response[T]])
  extends retrofit2.Call[T] {
  private val callCount = new AtomicInteger(0)

  /** Number of times `execute()` has been called so far. */
  def executedCount: Int = callCount.get()

  override def execute(): retrofit2.Response[T] = {
    val attemptIndex = callCount.getAndIncrement()
    val behaviour =
      if (attemptIndex < behaviours.size) behaviours(attemptIndex) else behaviours.last
    behaviour()
  }

  override def enqueue(callback: retrofit2.Callback[T]): Unit =
    throw new UnsupportedOperationException("StubCall only supports execute()")

  override def isExecuted(): Boolean      = callCount.get() > 0
  override def cancel(): Unit             = ()
  override def isCanceled(): Boolean      = false
  override def clone(): retrofit2.Call[T] = this
  override def request(): okhttp3.Request = null
}
