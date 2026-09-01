package im.paideia.app

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.ergoplatform.appkit.TransactionBox
import org.ergoplatform.appkit.UnsignedTransaction
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.impl.UnsignedTransactionImpl
import scorex.util.encode.Base16
import sigma.interpreter.ContextExtension
import sigma.serialization.ValueSerializer

import scala.collection.JavaConverters._

/** One token amount, EIP-12 shape - port of paideia-state's `models.MToken`
  * (`app/models/MToken.scala`). `amount` is a decimal string (not a JSON number):
  * nanoERG/ token amounts routinely exceed `Number.MAX_SAFE_INTEGER`, which is exactly
  * why EIP-12 specifies box `value` and token `amount` as strings.
  */
case class Eip12Token(tokenId: String, amount: String)

/** One EIP-12 input (or data input) box - port of paideia-state's `models.MInput`
  * (`app/models/MInput.scala`).
  *
  * @param extension
  *   this input's context extension (the `ContextVar`s a `PaideiaTransaction` attaches
  *   via `InputBox.withContextVars` - see e.g. `StakeTransaction`'s `contextVars`), keyed
  *   by the context var's byte index (as a decimal string) and hex-encoded via
  *   `ValueSerializer`, matching how a signing wallet (Nautilus) expects EIP-12's
  *   `extension` object. Always empty for a data input in this codebase - nothing here
  *   ever attaches context vars to a data input.
  */
case class Eip12Input(
  boxId: String,
  value: String,
  ergoTree: String,
  assets: List[Eip12Token],
  additionalRegisters: Map[String, String],
  creationHeight: Int,
  transactionId: String,
  index: Int,
  extension: Map[String, String]
)

/** One EIP-12 output box - port of paideia-state's `models.MOutput`
  * (`app/models/MOutput.scala`): the same shape as [[Eip12Input]] minus the
  * spent-box-only fields (`boxId`/`transactionId`/`index`) an as-yet-unconfirmed output
  * doesn't have.
  */
case class Eip12Output(
  value: String,
  ergoTree: String,
  assets: List[Eip12Token],
  additionalRegisters: Map[String, String],
  creationHeight: Int
)

/** An unsigned transaction in the exact shape EIP-12's `sign_tx` (and therefore Nautilus'
  * `ergo.sign_tx`) accepts - port of paideia-state's `models.MUnsignedTransaction`
  * (`app/models/MUnsignedTransaction.scala`'s `apply(unsigned: UnsignedTransaction)`
  * overload; the CLI has no UI fee and always funds from the addresses the user gave it
  * directly, so the other two overloads - `uiFeeBox` and the `BoxOperations`-driven
  * sender-funding one - don't apply here; `UserBoxSelector` already did that job by the
  * time this is called).
  */
case class Eip12UnsignedTx(
  inputs: List[Eip12Input],
  dataInputs: List[Eip12Input],
  outputs: List[Eip12Output]
)

object Eip12UnsignedTx {

  private def registersOf(box: TransactionBox): Map[String, String] =
    box
      .getRegisters()
      .asScala
      .zipWithIndex
      .map { case (v, i) => (s"R${i + 4}", v.toHex()) }
      .toMap

  private def assetsOf(box: TransactionBox): List[Eip12Token] =
    box
      .getTokens()
      .asScala
      .map(t => Eip12Token(t.getId.toString, t.getValue.toString))
      .toList

  private def extensionOf(ext: ContextExtension): Map[String, String] =
    ext.values.map { case (k, v) =>
      (k.toString, Base16.encode(ValueSerializer.serialize(v)))
    }.toMap

  /** Builds the EIP-12 shape from an already-built `UnsignedTransaction` (i.e. after
    * `PaideiaTransaction.unsigned()`, `userInputs` already filled in by
    * [[UserBoxSelector]]) - mirrors `MUnsignedTransaction.apply(UnsignedTransaction)`
    * exactly, including its one non-obvious wrinkle: an input's `InputBox` (from
    * `unsigned.getInputs()`) does NOT itself carry the context extension a
    * `PaideiaTransaction` attached via `withContextVars` - that only survives on the
    * `ExtendedInputBox`es `UnsignedTransactionImpl.getBoxesToSpend()` returns, in the
    * same order as `getInputs()`. Data inputs have no such wrinkle (nothing in this
    * codebase ever attaches context vars to one), so `InputBoxImpl.getExtension()` is
    * read directly there.
    */
  def apply(unsigned: UnsignedTransaction): Eip12UnsignedTx = {
    val boxesToSpend =
      unsigned.asInstanceOf[UnsignedTransactionImpl].getBoxesToSpend().asScala

    val inputs = unsigned
      .getInputs()
      .asScala
      .zip(boxesToSpend)
      .map { case (inp, extended) =>
        val impl = inp.asInstanceOf[InputBoxImpl]
        Eip12Input(
          inp.getId().toString,
          inp.getValue().toString,
          inp.getErgoTree().bytesHex,
          assetsOf(inp),
          registersOf(inp),
          inp.getCreationHeight(),
          impl.getErgoBox().transactionId.toString,
          impl.getErgoBox().index,
          extensionOf(extended.extension)
        )
      }
      .toList

    val dataInputs = unsigned
      .getDataInputs()
      .asScala
      .map { inp =>
        val impl = inp.asInstanceOf[InputBoxImpl]
        Eip12Input(
          inp.getId().toString,
          inp.getValue().toString,
          inp.getErgoTree().bytesHex,
          assetsOf(inp),
          registersOf(inp),
          inp.getCreationHeight(),
          impl.getErgoBox().transactionId.toString,
          impl.getErgoBox().index,
          extensionOf(impl.getExtension())
        )
      }
      .toList

    val outputs = unsigned
      .getOutputs()
      .asScala
      .map { outp =>
        Eip12Output(
          outp.getValue().toString,
          outp.getErgoTree().bytesHex,
          assetsOf(outp),
          registersOf(outp),
          outp.getCreationHeight()
        )
      }
      .toList

    Eip12UnsignedTx(inputs, dataInputs, outputs)
  }

  private def tokensJson(tokens: List[Eip12Token]): JsonArray = {
    val arr = new JsonArray()
    tokens.foreach { t =>
      val o = new JsonObject()
      o.addProperty("tokenId", t.tokenId)
      o.addProperty("amount", t.amount)
      arr.add(o)
    }
    arr
  }

  private def registersJson(registers: Map[String, String]): JsonObject = {
    val o = new JsonObject()
    registers.toSeq.sortBy(_._1).foreach { case (k, v) => o.addProperty(k, v) }
    o
  }

  private def inputJson(inp: Eip12Input): JsonObject = {
    val o = new JsonObject()
    o.addProperty("boxId", inp.boxId)
    o.addProperty("value", inp.value)
    o.addProperty("ergoTree", inp.ergoTree)
    o.add("assets", tokensJson(inp.assets))
    o.add("additionalRegisters", registersJson(inp.additionalRegisters))
    o.addProperty("creationHeight", inp.creationHeight)
    o.addProperty("transactionId", inp.transactionId)
    o.addProperty("index", inp.index)
    val extension = new JsonObject()
    inp.extension.toSeq.sortBy(_._1).foreach { case (k, v) =>
      extension.addProperty(k, v)
    }
    o.add("extension", extension)
    o
  }

  private def outputJson(outp: Eip12Output): JsonObject = {
    val o = new JsonObject()
    o.addProperty("value", outp.value)
    o.addProperty("ergoTree", outp.ergoTree)
    o.add("assets", tokensJson(outp.assets))
    o.add("additionalRegisters", registersJson(outp.additionalRegisters))
    o.addProperty("creationHeight", outp.creationHeight)
    o
  }

  /** Renders `tx` as the EIP-12 JSON object a wallet's `ergo.sign_tx` (or a `--no-sign`
    * dump) expects.
    */
  def toJsonObject(tx: Eip12UnsignedTx): JsonObject = {
    val root      = new JsonObject()
    val inputsArr = new JsonArray()
    tx.inputs.foreach(i => inputsArr.add(inputJson(i)))
    root.add("inputs", inputsArr)
    val dataInputsArr = new JsonArray()
    tx.dataInputs.foreach(i => dataInputsArr.add(inputJson(i)))
    root.add("dataInputs", dataInputsArr)
    val outputsArr = new JsonArray()
    tx.outputs.foreach(o => outputsArr.add(outputJson(o)))
    root.add("outputs", outputsArr)
    root
  }

  def toJson(tx: Eip12UnsignedTx): String = toJsonObject(tx).toString
}
