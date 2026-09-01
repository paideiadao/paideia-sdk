package im.paideia.common.sync

import org.scalatest.funsuite.AnyFunSuite
import scala.collection.mutable
import scala.collection.JavaConverters._

/** Covers `NodeBlockSource.nodeCall`'s retry/backoff behaviour in isolation, via
  * hand-rolled `retrofit2.Call` stubs (`StubCall`) - no real datasource or HTTP
  * round-trip is involved. `maxAttempts` is kept small in the failing-attempt tests to
  * keep the (real) backoff sleeps short.
  */
class NodeBlockSourceSuite extends AnyFunSuite {

  private def newSource(
    maxAttempts: Int                               = 5,
    onRetry: mutable.Buffer[(String, Int, String)] = mutable.Buffer.empty
  ): (NodeBlockSource, mutable.Buffer[(String, Int, String)]) = {
    val retries = onRetry
    val source = new NodeBlockSource(
      datasource  = null,
      maxAttempts = maxAttempts,
      onRetry     = (desc, attempt, msg) => retries += ((desc, attempt, msg))
    )
    (source, retries)
  }

  private def success[T](body: T): retrofit2.Response[T] =
    retrofit2.Response.success(body)

  private def httpError[T](code: Int, message: String): retrofit2.Response[T] =
    retrofit2.Response.error(
      code,
      okhttp3.ResponseBody.create(okhttp3.MediaType.parse("text/plain"), message)
    )

  test("succeeds on the first try, with no retries") {
    val (source, retries) = newSource()
    val call              = new StubCall[String](Seq(() => success("ok")))

    val result = source.nodeCall[String]("getSomething")(call)

    assert(result == "ok")
    assert(call.executedCount == 1)
    assert(retries.isEmpty)
  }

  test("succeeds after transient failures, retrying the exact number of times needed") {
    val (source, retries) = newSource(maxAttempts = 5)
    val call = new StubCall[String](
      Seq(
        () => throw new java.io.IOException("connection reset"),
        () => httpError(503, "Service Unavailable"),
        () => success("ok")
      )
    )

    val result = source.nodeCall[String]("getSomething")(call)

    assert(result == "ok")
    assert(call.executedCount == 3)
    assert(retries.map(_._2) == Seq(1, 2)) // attempt numbers for the two failures
    assert(retries.forall(_._1 == "getSomething"))
    assert(retries(0)._3.contains("connection reset"))
    assert(retries(1)._3.contains("503"))
  }

  test(
    "exhausts maxAttempts and throws a RuntimeException naming the description, preserving the cause"
  ) {
    val (source, _) = newSource(maxAttempts = 2)
    val boom        = new java.io.IOException("node unreachable")
    val call        = new StubCall[String](Seq(() => throw boom))

    val ex = intercept[RuntimeException] {
      source.nodeCall[String]("getNodeInfo")(call)
    }

    assert(call.executedCount == 2)
    assert(ex.getMessage.contains("getNodeInfo"))
    assert(ex.getMessage.contains("node unreachable"))
    assert(ex.getCause eq boom)
  }

  test("exhausts maxAttempts on repeated HTTP failures and throws without a cause") {
    val (source, _) = newSource(maxAttempts = 2)
    val call = new StubCall[String](Seq(() => httpError(500, "Internal Server Error")))

    val ex = intercept[RuntimeException] {
      source.nodeCall[String]("getNodeInfo")(call)
    }

    assert(call.executedCount == 2)
    assert(ex.getMessage.contains("getNodeInfo"))
    assert(ex.getMessage.contains("500"))
    assert(ex.getCause == null)
  }

  test(
    "treats an empty header-id list as invalid and retries until a non-empty list arrives"
  ) {
    val (source, retries)                    = newSource(maxAttempts = 3)
    val emptyList: java.util.List[String]    = List.empty[String].asJava
    val nonEmptyList: java.util.List[String] = List("headerId1").asJava
    val call = new StubCall[java.util.List[String]](
      Seq(
        () => success(emptyList),
        () => success(nonEmptyList)
      )
    )

    val result = source.nodeCall[java.util.List[String]](
      "getFullBlockAt(100)",
      valid = (l: java.util.List[String]) => !l.isEmpty
    )(call)

    assert(result.asScala == Seq("headerId1"))
    assert(call.executedCount == 2)
    assert(retries.size == 1)
    assert(retries.head._1 == "getFullBlockAt(100)")
    assert(retries.head._2 == 1)
    assert(retries.head._3.contains("invalid body"))
  }
}
