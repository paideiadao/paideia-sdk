package im.paideia.cli

import com.google.gson.JsonElement
import com.google.gson.JsonParser

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import scala.collection.JavaConverters._

/** Plain HTTP calls against an Ergo node's non-indexed transaction endpoints - used only
  * by the signing flow (submitting a wallet-signed tx, and polling for it to show up) -
  * kept separate from the sdk's own `NodeCalls`/`NodeBlockSource`/`IndexedNodeClient`
  * (whose retrofit-based retry loop is built for the sync path's own needs) since this is
  * a handful of one-shot calls with their own, much simpler, poll-and-tolerate-failure
  * behaviour (see `Main.waitForSubmission`). Uses `java.net.http.HttpClient` (built into
  * the JDK since 11) rather than pulling in a new HTTP dependency.
  */
object NodeHttp {

  private val client: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  /** Rewrites a wallet's EIP-12 `sign_tx` result into the shape the node's own `POST
    * /transactions` decoder accepts. The two differ in exactly one way: EIP-12 mandates
    * box `value` and token `amount` as decimal STRINGS (JavaScript numbers lose precision
    * past 2^53), while the node's circe decoders require JSON numbers and reject strings.
    * Every `"value"`/`"amount"` string primitive anywhere in the tree is converted to a
    * number (gson emits `Long`s exactly, no precision loss); everything else - including
    * fields the node ignores, like each output's `boxId` - passes through untouched.
    */
  private[cli] def eip12ToNodeJson(signedTxJson: String): String = {
    def walk(el: JsonElement): Unit =
      if (el.isJsonObject) {
        val obj = el.getAsJsonObject
        obj.entrySet().asScala.foreach { entry =>
          val v = entry.getValue
          if (
            (entry.getKey == "value" || entry.getKey == "amount") &&
            v.isJsonPrimitive && v.getAsJsonPrimitive.isString
          )
            entry.setValue(
              new com.google.gson.JsonPrimitive(java.lang.Long.parseLong(v.getAsString))
            )
          else walk(v)
        }
      } else if (el.isJsonArray)
        el.getAsJsonArray.asScala.foreach(walk)

    // (the instance API - the static parseString only exists from gson 2.8.6, and the
    // version appkit's restapi client drags in is older)
    val root = new JsonParser().parse(signedTxJson)
    walk(root)
    root.toString
  }

  /** Submits `signedTxJson` (a wallet's EIP-12 `sign_tx` result; converted to the node's
    * own JSON dialect via [[eip12ToNodeJson]] first) to `nodeUrl`.
    *
    * @return
    *   `Right(txId)` on a 2xx response (the node's response body is the tx id as a JSON
    *   string literal, e.g. `"abcd..."` - the surrounding quotes are stripped), or
    *   `Left(message)` describing the failure otherwise.
    */
  def submitSignedTx(nodeUrl: String, signedTxJson: String): Either[String, String] =
    try {
      val request = HttpRequest
        .newBuilder(URI.create(s"$nodeUrl/transactions"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(eip12ToNodeJson(signedTxJson)))
        .build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() / 100 == 2)
        Right(response.body().trim.stripPrefix("\"").stripSuffix("\""))
      else
        Left(
          s"node rejected the transaction: HTTP ${response.statusCode()}: ${response.body()}"
        )
    } catch {
      case e: Exception => Left(s"failed to reach node: ${e.getMessage}")
    }

  private def get(nodeUrl: String, path: String): Int = {
    val request = HttpRequest.newBuilder(URI.create(s"$nodeUrl$path")).GET().build()
    client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
  }

  /** Whether `txId` is currently sitting in the node's mempool. */
  def isUnconfirmed(nodeUrl: String, txId: String): Boolean =
    get(nodeUrl, s"/transactions/unconfirmed/byTransactionId/$txId") == 200

  /** Whether `txId` is confirmed on-chain, per the node's indexed `blockchain` API (the
    * same index [[im.paideia.common.sync.IndexedNodeClient]] requires - see the CLI's own
    * design notes on that requirement).
    */
  def isConfirmed(nodeUrl: String, txId: String): Boolean =
    get(nodeUrl, s"/blockchain/transaction/byId/$txId") == 200
}
