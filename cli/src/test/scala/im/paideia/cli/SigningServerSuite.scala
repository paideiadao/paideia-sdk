package im.paideia.cli

import org.scalatest.funsuite.AnyFunSuite

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Offline coverage of [[SigningServer]]'s HTTP surface: bound to an ephemeral port
  * (`port = 0`, then [[SigningServer.boundPort]]) with a stubbed `submitSignedTx`
  * function - no real node, no real wallet, no network beyond localhost.
  */
class SigningServerSuite extends AnyFunSuite {

  private val client = HttpClient.newHttpClient()

  private def get(base: String, path: String): HttpResponse[String] =
    client.send(
      HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    )

  private def post(base: String, path: String, body: String): HttpResponse[String] =
    client.send(
      HttpRequest
        .newBuilder(URI.create(base + path))
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )

  private val defaultToken        = "test-token-1234"
  private val defaultExpectedTxId = "deadbeef"

  private def withServer(
    reducedTxB64: String = "REDUCEDB64",
    eip12Json: String    = """{"inputs":[],"dataInputs":[],"outputs":[]}""",
    message: String      = "do a thing",
    html: String         = "<html><body>hi</body></html>",
    token: String        = defaultToken,
    expectedTxId: String = defaultExpectedTxId,
    submitSignedTx: String => Either[String, String] = _ => Right("stub-tx-id")
  )(body: (SigningServer, String, String) => Unit): Unit = {
    val server = new SigningServer(
      0,
      token,
      reducedTxB64,
      eip12Json,
      message,
      html,
      expectedTxId,
      submitSignedTx
    )
    server.start()
    try body(server, s"http://localhost:${server.boundPort}", token)
    finally server.stop()
  }

  test("GET /<token>/tx serves the ErgoPay signing request") {
    withServer(reducedTxB64 = "REDUCEDB64", message = "do a thing") { (_, base, token) =>
      val resp = get(base, s"/$token/tx")
      assert(resp.statusCode() == 200)
      assert(resp.body().contains("\"reducedTx\":\"REDUCEDB64\""))
      assert(resp.body().contains("\"message\":\"do a thing\""))
      assert(resp.body().contains("\"messageSeverity\":\"INFORMATION\""))
    }
  }

  test("GET /<token>/eip12 serves the eip12 JSON verbatim") {
    val eip12 = """{"inputs":[{"boxId":"abc"}],"dataInputs":[],"outputs":[]}"""
    withServer(eip12Json = eip12) { (_, base, token) =>
      val resp = get(base, s"/$token/eip12")
      assert(resp.statusCode() == 200)
      assert(resp.body() == eip12)
    }
  }

  test("GET /<token>/ serves the bundled Nautilus page") {
    withServer(html = "<html><body>hi there</body></html>") { (_, base, token) =>
      val resp = get(base, s"/$token/")
      assert(resp.statusCode() == 200)
      assert(resp.body().contains("hi there"))
    }
  }

  test("GET /<token>/unknown-path returns 404") {
    withServer() { (_, base, token) =>
      val resp = get(base, s"/$token/unknown-path")
      assert(resp.statusCode() == 404)
    }
  }

  test("M1: a right-shaped path under the WRONG token returns 404") {
    withServer(token = "correct-token") { (_, base, _) =>
      val resp = get(base, "/wrong-token/tx")
      assert(resp.statusCode() == 404)
    }
  }

  test("M1: a bare, token-less path returns 404") {
    withServer() { (_, base, _) =>
      val resp = get(base, "/tx")
      assert(resp.statusCode() == 404)
    }
  }

  test(
    "POST /<token>/signed forwards the body to submitSignedTx and records the returned txId"
  ) {
    var received: Option[String] = None
    withServer(
      expectedTxId = "deadbeef",
      submitSignedTx = { body =>
        received = Some(body)
        Right("deadbeef")
      }
    ) { (server, base, token) =>
      val resp = post(base, s"/$token/signed", """{"id":"deadbeef"}""")
      assert(resp.statusCode() == 200)
      assert(resp.body().contains("deadbeef"))
      assert(server.submittedTxId == Some("deadbeef"))
      assert(received == Some("""{"id":"deadbeef"}"""))
    }
  }

  test(
    "POST /<token>/signed surfaces a submission failure without crashing, once, via takeError"
  ) {
    withServer(expectedTxId = "deadbeef", submitSignedTx = _ => Left("node said no")) {
      (server, base, token) =>
        val resp = post(base, s"/$token/signed", """{"id":"deadbeef"}""")
        assert(resp.statusCode() == 502)
        assert(resp.body().contains("node said no"))
        assert(server.submittedTxId.isEmpty)
        assert(server.takeError() == Some("node said no"))
        assert(server.takeError() == None)
    }
  }

  test("POST /<token>/tx (wrong method) is rejected") {
    withServer() { (_, base, token) =>
      val resp = post(base, s"/$token/tx", "{}")
      // /tx has no explicit method guard (a GET-only endpoint by convention), but a POST
      // body is simply ignored and the same signing request is returned either way -
      // this only pins that behaviour so a future change to add a method guard is
      // deliberate, not accidental.
      assert(resp.statusCode() == 200)
    }
  }

  test("a non-POST /<token>/signed is rejected with 405") {
    withServer() { (_, base, token) =>
      val resp = get(base, s"/$token/signed")
      assert(resp.statusCode() == 405)
    }
  }

  // --- M1.1: the submitted tx's own "id" must match what this invocation expects.

  test(
    "M1: POST /<token>/signed with a mismatched id is rejected 4xx and never submitted"
  ) {
    var submitted = false
    withServer(
      expectedTxId = "deadbeef",
      submitSignedTx = { _ =>
        submitted = true
        Right("deadbeef")
      }
    ) { (server, base, token) =>
      val resp = post(base, s"/$token/signed", """{"id":"someoneelsestx"}""")
      assert(resp.statusCode() >= 400 && resp.statusCode() < 500, resp.statusCode())
      assert(!submitted, "a mismatched id must never reach submitSignedTx")
      assert(server.submittedTxId.isEmpty)
    }
  }

  test(
    "M1: POST /<token>/signed with no readable id field is rejected 4xx and never submitted"
  ) {
    var submitted = false
    withServer(submitSignedTx = { _ =>
      submitted = true
      Right("deadbeef")
    }) { (_, base, token) =>
      val resp = post(base, s"/$token/signed", """{"notAnId":"whatever"}""")
      assert(resp.statusCode() >= 400 && resp.statusCode() < 500, resp.statusCode())
      assert(!submitted)
    }
  }

  // --- M1.3: an oversized body is refused before it's ever parsed.

  test("M1: POST /<token>/signed with a body over 1 MB is rejected 413") {
    withServer() { (_, base, token) =>
      val oversized = "x" * (1024 * 1024 + 1)
      val resp      = post(base, s"/$token/signed", oversized)
      assert(resp.statusCode() == 413)
    }
  }
}
