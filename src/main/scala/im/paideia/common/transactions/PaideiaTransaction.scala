package im.paideia.common.transactions

import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.ReducedTransaction
import org.ergoplatform.appkit.UnsignedTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl

import scala.collection.JavaConverters._

/**
  * Trait representing a transaction with inputs, outputs, fees and other information needed for signing.
  *
  * @constructor create a new PaideiaTransaction.
  * @tparam inputs type of the list of input boxes for this transaction.
  * @tparam outputs type of the list of output boxes for this transaction.
  * @tparam tokensToBurn type of the list of tokens that will be burned in this transaction.
  */
trait PaideiaTransaction {

  /**
    * The list of input boxes for this transaction
    */
  var inputs: List[InputBox] = _

  /**
    * The list of user input boxes for this transaction
    */
  var userInputs: List[InputBox] = List[InputBox]()

  /**
    * The list of data input boxes for this transaction
    */
  var dataInputs: List[InputBox] = List[InputBox]()

  /**
    * The list of output boxes for this transaction
    */
  var outputs: List[OutBox] = _

  /**
    * The list of tokens that will be burned in this transaction
    */
  var tokensToBurn: List[ErgoToken] = List[ErgoToken]()

  /**
    * The fee for this transaction
    */
  var fee: Long = _

  /**
    * The context of blockchain for this transaction
    */
  var ctx: BlockchainContextImpl = _

  /**
    * The address where change will be sent after this transaction is executed
    */
  var changeAddress: ErgoAddress = _

  /**
    * Create an unsigned transaction using inputs, outputs, tokensToBurn, fee, ctx and changeAddress.
    *
    * @return an UnsignedTransaction.
    */
  def unsigned(): UnsignedTransaction = {
    this.ctx
      .newTxBuilder()
      .outputs(this.outputs: _*)
      .sendChangeTo(this.changeAddress)
      .tokensToBurn(this.tokensToBurn: _*)
      .preHeader(this.ctx.createPreHeader().build())
      .fee(this.fee)
      .boxesToSpend((this.inputs ++ this.userInputs).asJava)
      .withDataInputs(this.dataInputs.asJava)
      .build()
  }

  /**
    * Reduce the unsigned transaction to a reduced one.
    *
    * @return a ReducedTransaction.
    */
  def reduced(): ReducedTransaction = {
    this.ctx.newProverBuilder.build.reduce(this.unsigned, 0)
  }
}
