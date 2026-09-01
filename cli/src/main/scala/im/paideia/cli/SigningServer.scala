package im.paideia.cli

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.Executors
import scala.io.Source
import scala.util.Try

/** A short-lived local HTTP server exposing exactly one unsigned transaction for signing,
  * two ways at once:
  *
  *   - `GET /<token>/tx`: an ErgoPay signing request (`{"reducedTx", "message",
  *     "messageSeverity"}`) - scanned as `ergopay://<host>:<port>/<token>/tx` by a mobile
  *     Ergo wallet, which fetches, signs and (per the ErgoPay protocol - there's no
  *     `replyTo` in this response) submits the transaction to the network *itself*, never
  *     touching this server again. `Main.waitForSubmission`'s node-polling half is what
  *     notices that path completed.
  *   - `GET /<token>/`: an embedded page (`nautilus.html`) for a desktop Nautilus wallet
  *     \- it fetches `GET <token>/eip12` (a relative path, so it resolves under whatever
  *     token prefix the page itself was served at), calls `ergo.sign_tx`, and POSTs the
  *     signed result to `POST <token>/signed`, which THIS server submits to the node on
  *     the page's behalf (a page has no node access of its own) - see `submitSignedTx`.
  *
  * Every endpoint lives under a random per-invocation `token` (see
  * [[SigningServer$.newToken]]) rather than at a fixed, guessable path: this server binds
  * to all interfaces (so a phone on the same LAN can reach it), and without an
  * unguessable path in the URL, anyone else on that LAN who merely knew the port could
  * fetch the pending transaction and race the legitimate wallet to sign and submit it. A
  * request for anything other than exactly `/<token>/tx`, `/<token>/eip12`, `/<token>/`
  * or `/<token>/signed` - including the right sub-path under the WRONG token - gets a
  * plain 404 (nothing here reveals whether a given token is merely wrong or just
  * malformed).
  *
  * Lives only for the duration of one CLI transaction command - `start()`/`stop()`
  * bracket exactly one `Main.signAndSubmit` call.
  *
  * @param expectedTxId
  *   the id `unsignedTx` (and therefore any faithfully-signed version of it - Ergo tx ids
  *   are computed from inputs/dataInputs/outputs only, never signatures) must have.
  *   `/signed` rejects (400) any submitted payload whose own `"id"` field doesn't match -
  *   a wallet extension bug or a stray unrelated POST could otherwise have this server
  *   forward an arbitrary transaction to the node under this CLI invocation's name.
  * @param submitSignedTx
  *   submits a wallet-signed tx (forwarded verbatim from `POST /<token>/signed`'s body,
  *   once its `"id"` has been checked) and returns its id, or a failure message -
  *   defaults to [[NodeHttp.submitSignedTx]] against a real node; injectable so tests can
  *   stub it without a live node (see `SigningServerSuite`).
  */
class SigningServer(
  port: Int,
  token: String,
  reducedTxB64: String,
  eip12Json: String,
  message: String,
  nautilusPageHtml: String,
  expectedTxId: String,
  submitSignedTx: String => Either[String, String]
) {

  /** A request body larger than this is refused (413) before it's even parsed - a signed
    * Ergo tx is, in the extreme, a few hundred KB; 1 MB is a generous ceiling that still
    * bounds how much a misbehaving (or malicious) POST to this locally-bound server can
    * make it buffer in memory.
    */
  private val maxBodyBytes = 1024 * 1024

  private class BodyTooLargeException extends RuntimeException

  private val server = HttpServer.create(new InetSocketAddress(port), 0)
  server.setExecutor(Executors.newCachedThreadPool())

  /** Set exactly once, by the `/signed` handler, the moment a wallet-submitted tx is
    * accepted by the node - `Main.waitForSubmission` polls this alongside its own
    * node-side confirmation check.
    */
  @volatile private var _submittedTxId: Option[String] = None

  /** The most recent `/signed` submission failure, if any - surfaced so
    * `Main.waitForSubmission` can log it once instead of silently retrying forever. Reset
    * to `None` whenever it's read via [[takeError]], so the same failure isn't logged
    * twice. Every read and write goes through `this`'s monitor (see [[takeError]] and the
    * `/signed` handler) - without that, a write racing a concurrent `takeError()` read
    * could interleave in a way that loses the error entirely (a plain `@volatile` only
    * guarantees visibility of whichever write happens to land last, not a consistent
    * read-then-clear).
    */
  private var _lastError: Option[String] = None

  def submittedTxId: Option[String] = _submittedTxId

  def takeError(): Option[String] = synchronized {
    val e = _lastError
    _lastError = None
    e
  }

  private def setLastError(error: String): Unit = synchronized {
    _lastError = Some(error)
  }

  private def bodyOf(exchange: HttpExchange): String = {
    val out   = new ByteArrayOutputStream()
    val in    = exchange.getRequestBody
    val buf   = new Array[Byte](4096)
    var total = 0
    var n     = in.read(buf)
    while (n >= 0) {
      total += n
      if (total > maxBodyBytes) throw new BodyTooLargeException
      out.write(buf, 0, n)
      n = in.read(buf)
    }
    new String(out.toByteArray, StandardCharsets.UTF_8)
  }

  private def respond(
    exchange: HttpExchange,
    status: Int,
    contentType: String,
    body: String
  ): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.add("Content-Type", contentType)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val os = exchange.getResponseBody
    try os.write(bytes)
    finally os.close()
  }

  private def respondJson(exchange: HttpExchange, status: Int, body: String): Unit =
    respond(exchange, status, "application/json; charset=utf-8", body)

  private def jsonString(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

  private def notFound(exchange: HttpExchange): Unit =
    respond(exchange, 404, "text/plain; charset=utf-8", "not found")

  private def handleTx(exchange: HttpExchange): Unit =
    respondJson(
      exchange,
      200,
      s"""{"reducedTx":${jsonString(reducedTxB64)},"message":${jsonString(message)},""" +
        """"messageSeverity":"INFORMATION"}"""
    )

  private def handleEip12(exchange: HttpExchange): Unit =
    respondJson(exchange, 200, eip12Json)

  private def handleRoot(exchange: HttpExchange): Unit =
    respond(exchange, 200, "text/html; charset=utf-8", nautilusPageHtml)

  /** Extracts the submitted tx's own `"id"` field, tolerating any parse failure by
    * treating it the same as a mismatch (`None`) - a malformed body has no business being
    * forwarded to the node under this invocation's expected id either way.
    */
  private def idOf(signedTxJson: String): Option[String] =
    Try(
      new JsonParser().parse(signedTxJson).getAsJsonObject.get("id").getAsString
    ).toOption

  private def handleSigned(exchange: HttpExchange): Unit =
    if (exchange.getRequestMethod != "POST")
      respond(exchange, 405, "text/plain; charset=utf-8", "expected POST")
    else
      try {
        val signedTxJson = bodyOf(exchange)
        idOf(signedTxJson) match {
          case Some(id) if id == expectedTxId =>
            submitSignedTx(signedTxJson) match {
              case Right(txId) =>
                _submittedTxId = Some(txId)
                respondJson(exchange, 200, s"""{"txId":${jsonString(txId)}}""")
              case Left(error) =>
                setLastError(error)
                respondJson(exchange, 502, s"""{"error":${jsonString(error)}}""")
            }
          case Some(id) =>
            respondJson(
              exchange,
              400,
              s"""{"error":${jsonString(
                  s"submitted transaction id $id does not match the expected $expectedTxId"
                )}}"""
            )
          case None =>
            respondJson(
              exchange,
              400,
              s"""{"error":${jsonString(
                  "submitted body has no readable \"id\" field"
                )}}"""
            )
        }
      } catch {
        case _: BodyTooLargeException =>
          respond(exchange, 413, "text/plain; charset=utf-8", "request body too large")
      }

  server.createContext(
    "/",
    new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        val prefix = s"/$token"
        val path   = exchange.getRequestURI.getPath
        if (!path.startsWith(prefix)) notFound(exchange)
        else
          path.stripPrefix(prefix) match {
            case "/tx"     => handleTx(exchange)
            case "/eip12"  => handleEip12(exchange)
            case "" | "/"  => handleRoot(exchange)
            case "/signed" => handleSigned(exchange)
            case _         => notFound(exchange)
          }
      }
    }
  )

  def start(): Unit = server.start()

  def stop(): Unit = server.stop(0)

  /** The port actually bound - identical to the requested `port` unless that was `0`
    * ("pick any free port"), which `SigningServerSuite` relies on to avoid hardcoding a
    * port number in tests.
    */
  def boundPort: Int = server.getAddress.getPort
}

object SigningServer {

  private def readResource(name: String): String = {
    val stream = getClass.getResourceAsStream(s"/$name")
    if (stream == null)
      throw new IllegalStateException(s"missing bundled resource: $name")
    try Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()
  }

  /** A fresh 128-bit (16-byte) random token, hex-encoded (32 hex characters) via
    * `SecureRandom` - see the class scaladoc for why every endpoint lives under one of
    * these instead of a fixed path.
    */
  def newToken(): String = {
    val bytes = new Array[Byte](16)
    new SecureRandom().nextBytes(bytes)
    bytes.map(b => f"$b%02x").mkString
  }

  /** Production wiring: submits a wallet-signed tx to `nodeUrl` via [[NodeHttp]], and
    * serves the bundled `nautilus.html` (`cli/src/main/resources/nautilus.html`) at
    * `/<token>/`.
    */
  def apply(
    port: Int,
    token: String,
    reducedTxB64: String,
    eip12Json: String,
    message: String,
    expectedTxId: String,
    nodeUrl: String
  ): SigningServer =
    new SigningServer(
      port,
      token,
      reducedTxB64,
      eip12Json,
      message,
      readResource("nautilus.html"),
      expectedTxId,
      signedTxJson => NodeHttp.submitSignedTx(nodeUrl, signedTxJson)
    )
}
