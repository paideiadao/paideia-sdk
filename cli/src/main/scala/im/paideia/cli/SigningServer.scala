package im.paideia.cli

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import scala.io.Source

/** A short-lived local HTTP server exposing exactly one unsigned transaction for signing,
  * two ways at once:
  *
  *   - `GET /tx`: an ErgoPay signing request (`{"reducedTx", "message",
  *     "messageSeverity"}`) - scanned as `ergopay://<host>:<port>/tx` by a mobile Ergo
  *     wallet, which fetches, signs and (per the ErgoPay protocol - there's no `replyTo`
  *     in this response) submits the transaction to the network *itself*, never touching
  *     this server again. `Main.waitForSubmission`'s node-polling half is what notices
  *     that path completed.
  *   - `GET /`: an embedded page (`nautilus.html`) for a desktop Nautilus wallet - it
  *     fetches `GET /eip12`, calls `ergo.sign_tx`, and POSTs the signed result to `POST
  *     /signed`, which THIS server submits to the node on the page's behalf (a page has
  *     no node access of its own) - see `submitSignedTx`.
  *
  * Lives only for the duration of one CLI transaction command - `start()`/`stop()`
  * bracket exactly one `Main.signAndSubmit` call.
  *
  * @param submitSignedTx
  *   submits a wallet-signed tx (forwarded verbatim from `POST /signed`'s body) and
  *   returns its id, or a failure message - defaults to [[NodeHttp.submitSignedTx]]
  *   against a real node; injectable so tests can stub it without a live node (see
  *   `SigningServerSuite`).
  */
class SigningServer(
  port: Int,
  reducedTxB64: String,
  eip12Json: String,
  message: String,
  nautilusPageHtml: String,
  submitSignedTx: String => Either[String, String]
) {

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
    * twice.
    */
  @volatile private var _lastError: Option[String] = None

  def submittedTxId: Option[String] = _submittedTxId

  def takeError(): Option[String] = synchronized {
    val e = _lastError
    _lastError = None
    e
  }

  private def bodyOf(exchange: HttpExchange): String = {
    val out = new ByteArrayOutputStream()
    val in  = exchange.getRequestBody
    val buf = new Array[Byte](4096)
    var n   = in.read(buf)
    while (n >= 0) {
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

  server.createContext(
    "/tx",
    new HttpHandler {
      override def handle(exchange: HttpExchange): Unit =
        respondJson(
          exchange,
          200,
          s"""{"reducedTx":${jsonString(reducedTxB64)},"message":${jsonString(
              message
            )},""" +
            """"messageSeverity":"INFORMATION"}"""
        )
    }
  )

  server.createContext(
    "/eip12",
    new HttpHandler {
      override def handle(exchange: HttpExchange): Unit =
        respondJson(exchange, 200, eip12Json)
    }
  )

  server.createContext(
    "/",
    new HttpHandler {
      override def handle(exchange: HttpExchange): Unit =
        if (exchange.getRequestURI.getPath == "/")
          respond(exchange, 200, "text/html; charset=utf-8", nautilusPageHtml)
        else
          respond(exchange, 404, "text/plain; charset=utf-8", "not found")
    }
  )

  server.createContext(
    "/signed",
    new HttpHandler {
      override def handle(exchange: HttpExchange): Unit =
        if (exchange.getRequestMethod != "POST")
          respond(exchange, 405, "text/plain; charset=utf-8", "expected POST")
        else {
          val signedTxJson = bodyOf(exchange)
          submitSignedTx(signedTxJson) match {
            case Right(txId) =>
              _submittedTxId = Some(txId)
              respondJson(exchange, 200, s"""{"txId":${jsonString(txId)}}""")
            case Left(error) =>
              _lastError = Some(error)
              respondJson(exchange, 502, s"""{"error":${jsonString(error)}}""")
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

  /** Production wiring: submits a wallet-signed tx to `nodeUrl` via [[NodeHttp]], and
    * serves the bundled `nautilus.html` (`cli/src/main/resources/nautilus.html`) at `/`.
    */
  def apply(
    port: Int,
    reducedTxB64: String,
    eip12Json: String,
    message: String,
    nodeUrl: String
  ): SigningServer =
    new SigningServer(
      port,
      reducedTxB64,
      eip12Json,
      message,
      readResource("nautilus.html"),
      signedTxJson => NodeHttp.submitSignedTx(nodeUrl, signedTxJson)
    )
}
