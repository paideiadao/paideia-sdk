package im.paideia.common.transactions

import org.ergoplatform.ErgoAddress
import org.ergoplatform.sdk.ErgoToken
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.ReducedTransaction
import org.ergoplatform.appkit.UnsignedTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl

import scala.collection.JavaConverters._
import scorex.util.ModifierId
import org.ergoplatform.sdk.wallet.{AssetUtils, TokensMap}
import scala.collection._
import org.ergoplatform.appkit.ErgoContract
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.impl.UnsignedTransactionImpl
import org.ergoplatform.ErgoLikeTransaction
import org.ergoplatform.UnsignedErgoLikeTransaction

/** Trait representing a transaction with inputs, outputs, fees and other information
  * needed for signing.
  *
  * @constructor
  *   create a new PaideiaTransaction.
  * @tparam inputs
  *   type of the list of input boxes for this transaction.
  * @tparam outputs
  *   type of the list of output boxes for this transaction.
  * @tparam tokensToBurn
  *   type of the list of tokens that will be burned in this transaction.
  */
trait PaideiaTransaction {

  var minimizeChangeBox: Boolean = true

  /** The list of input boxes for this transaction
    */
  var inputs: List[InputBox] = _

  /** The list of user input boxes for this transaction
    */
  var userInputs: List[InputBox] = List[InputBox]()

  /** The list of data input boxes for this transaction
    */
  var dataInputs: List[InputBox] = List[InputBox]()

  /** The list of output boxes for this transaction
    */
  var outputs: List[OutBox] = _

  /** The list of tokens that will be burned in this transaction
    */
  var tokensToBurn: List[ErgoToken] = List[ErgoToken]()

  /** The fee for this transaction
    */
  var fee: Long = _

  /** The context of blockchain for this transaction
    */
  var ctx: BlockchainContextImpl = _

  /** The address where change will be sent after this transaction is executed
    */
  var changeAddress: Address = _

  /** Create an unsigned transaction using inputs, outputs, tokensToBurn, fee, ctx and
    * changeAddress.
    *
    * @return
    *   an UnsignedTransaction.
    */
  def unsigned(): UnsignedTransaction = {
    var inputBalance = 0L
    val inputAssets  = mutable.Map[ModifierId, Long]()

    // select all input boxes - we only validate here
    inputs.foreach { box: InputBox =>
      inputBalance = inputBalance + box.getValue()
      AssetUtils.mergeAssetsMut(
        inputAssets,
        box.getTokens().asScala.map(t => ModifierId @@ t.id.toString -> t.value).toMap
      )
    }

    var outputBalance = fee
    val outputAssets  = mutable.Map[ModifierId, Long]()

    // select all input boxes - we only validate here
    outputs.foreach { box: OutBox =>
      outputBalance = outputBalance + box.getValue()
      AssetUtils.mergeAssetsMut(
        outputAssets,
        box.getTokens().asScala.map(t => ModifierId @@ t.id.toString -> t.value).toMap
      )
    }

    AssetUtils.mergeAssetsMut(
      outputAssets,
      tokensToBurn.map(t => ModifierId @@ t.id.toString -> t.value).toMap
    )

    val outputAssetsNoMinted =
      outputAssets.filter(t => t._1 != inputs(0).getId().toString()).toMap

    if (inputBalance > outputBalance) {
      AssetUtils.subtractAssetsMut(inputAssets, outputAssetsNoMinted)
      val changeTokens = inputAssets.map(t => new ErgoToken(t._1, t._2)).toList
      var changeOutput =
        if (changeTokens.size > 0)
          ctx
            .newTxBuilder()
            .outBoxBuilder()
            .contract(changeAddress.toErgoContract())
            .value(inputBalance - outputBalance)
            .tokens(changeTokens: _*)
            .build()
        else
          ctx
            .newTxBuilder()
            .outBoxBuilder()
            .contract(changeAddress.toErgoContract())
            .value(inputBalance - outputBalance)
            .build()
      if (minimizeChangeBox) {
        val minChangeValue = changeOutput.getBytesWithNoRef().size * 360
        fee = fee + changeOutput.getValue() - minChangeValue
        changeOutput =
          if (changeTokens.size > 0)
            ctx
              .newTxBuilder()
              .outBoxBuilder()
              .contract(changeAddress.toErgoContract())
              .value(minChangeValue)
              .tokens(changeTokens: _*)
              .build()
          else
            ctx
              .newTxBuilder()
              .outBoxBuilder()
              .contract(changeAddress.toErgoContract())
              .value(minChangeValue)
              .build()
      }
      outputs = outputs ++ List(changeOutput)
    }

    val unsignedTx = this.ctx
      .newTxBuilder()
      .outputs(this.outputs: _*)
      .sendChangeTo(this.changeAddress)
      .tokensToBurn(this.tokensToBurn: _*)
      .preHeader(this.ctx.createPreHeader().build())
      .fee(this.fee)
      .boxesToSpend((this.inputs ++ this.userInputs).asJava)
      .withDataInputs(this.dataInputs.asJava)
      .build()
      .asInstanceOf[UnsignedTransactionImpl]

    if (inputBalance > outputBalance) {
      val newOutputList = unsignedTx
        .getTx()
        .outputCandidates
        .slice(0, unsignedTx.getTx().outputCandidates.size - 2) ++ unsignedTx
        .getTx()
        .outputCandidates
        .slice(
          unsignedTx.getTx().outputCandidates.size - 1,
          unsignedTx.getTx().outputCandidates.size
        ) ++
        unsignedTx
          .getTx()
          .outputCandidates
          .slice(
            unsignedTx.getTx().outputCandidates.size - 2,
            unsignedTx.getTx().outputCandidates.size - 1
          )
      new UnsignedTransactionImpl(
        new UnsignedErgoLikeTransaction(
          unsignedTx.getTx().inputs,
          unsignedTx.getTx().dataInputs,
          newOutputList
        ),
        unsignedTx.getBoxesToSpend(),
        unsignedTx.getDataBoxes(),
        unsignedTx.getChangeAddress(),
        unsignedTx.getStateContext(),
        ctx,
        unsignedTx.getTokensToBurn()
      )
    } else {
      unsignedTx
    }

  }

  /** Reduce the unsigned transaction to a reduced one.
    *
    * @return
    *   a ReducedTransaction.
    */
  def reduced(): ReducedTransaction = {
    this.ctx.newProverBuilder.build.reduce(this.unsigned, 0)
  }
}
