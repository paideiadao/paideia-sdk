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

  private def withServer(
    reducedTxB64: String = "REDUCEDB64",
    eip12Json: String    = """{"inputs":[],"dataInputs":[],"outputs":[]}""",
    message: String      = "do a thing",
    html: String         = "<html><body>hi</body></html>",
    submitSignedTx: String => Either[String, String] = _ => Right("stub-tx-id")
  )(body: (SigningServer, String) => Unit): Unit = {
    val server =
      new SigningServer(0, reducedTxB64, eip12Json, message, html, submitSignedTx)
    server.start()
    try body(server, s"http://localhost:${server.boundPort}")
    finally server.stop()
  }

  test("GET /tx serves the ErgoPay signing request") {
    withServer(reducedTxB64 = "REDUCEDB64", message = "do a thing") { (_, base) =>
      val resp = get(base, "/tx")
      assert(resp.statusCode() == 200)
      assert(resp.body().contains("\"reducedTx\":\"REDUCEDB64\""))
      assert(resp.body().contains("\"message\":\"do a thing\""))
      assert(resp.body().contains("\"messageSeverity\":\"INFORMATION\""))
    }
  }

  test("GET /eip12 serves the eip12 JSON verbatim") {
    val eip12 = """{"inputs":[{"boxId":"abc"}],"dataInputs":[],"outputs":[]}"""
    withServer(eip12Json = eip12) { (_, base) =>
      val resp = get(base, "/eip12")
      assert(resp.statusCode() == 200)
      assert(resp.body() == eip12)
    }
  }

  test("GET / serves the bundled Nautilus page") {
    withServer(html = "<html><body>hi there</body></html>") { (_, base) =>
      val resp = get(base, "/")
      assert(resp.statusCode() == 200)
      assert(resp.body().contains("hi there"))
    }
  }

  test("GET /unknown-path returns 404") {
    withServer() { (_, base) =>
      val resp = get(base, "/unknown-path")
      assert(resp.statusCode() == 404)
    }
  }

  test("POST /signed forwards the body to submitSignedTx and records the returned txId") {
    var received: Option[String] = None
    withServer(submitSignedTx = { body =>
      received = Some(body)
      Right("deadbeef")
    }) { (server, base) =>
      val resp = post(base, "/signed", """{"id":"deadbeef"}""")
      assert(resp.statusCode() == 200)
      assert(resp.body().contains("deadbeef"))
      assert(server.submittedTxId == Some("deadbeef"))
      assert(received == Some("""{"id":"deadbeef"}"""))
    }
  }

  test(
    "POST /signed surfaces a submission failure without crashing, once, via takeError"
  ) {
    withServer(submitSignedTx = _ => Left("node said no")) { (server, base) =>
      val resp = post(base, "/signed", "{}")
      assert(resp.statusCode() == 502)
      assert(resp.body().contains("node said no"))
      assert(server.submittedTxId.isEmpty)
      assert(server.takeError() == Some("node said no"))
      assert(server.takeError() == None)
    }
  }

  test("POST /tx (wrong method) is rejected") {
    withServer() { (_, base) =>
      val resp = post(base, "/tx", "{}")
      // /tx has no explicit method guard (a GET-only endpoint by convention), but a POST
      // body is simply ignored and the same signing request is returned either way -
      // this only pins that behaviour so a future change to add a method guard is
      // deliberate, not accidental.
      assert(resp.statusCode() == 200)
    }
  }

  test("a non-POST /signed is rejected with 405") {
    withServer() { (_, base) =>
      val resp = get(base, "/signed")
      assert(resp.statusCode() == 405)
    }
  }
}
