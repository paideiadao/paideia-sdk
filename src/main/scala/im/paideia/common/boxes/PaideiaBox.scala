package im.paideia.common.boxes

import im.paideia.util.Util
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.ErgoContract
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.impl.InputBoxImpl
import org.ergoplatform.appkit.impl.ScalaBridge
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import sigmastate.Values
import sigmastate.eval.CostingBox
import special.sigma.Box

/**
  * This trait represents a blockchain transaction output.
  */
trait PaideiaBox {
  // private fields representing the value, contract, tokens, registers, context vars, and blockchain context
  private var _value: Long                   = _
  private var _contract: ErgoContract        = _
  private var _tokens: List[ErgoToken]       = List[ErgoToken]()
  private var _registers: List[ErgoValue[_]] = List[ErgoValue[_]]()
  private var _contextVars: List[ContextVar] = _
  private var _ctx: BlockchainContextImpl    = _

  /**
    * Converts this output to an input box (used in spending transactions).
    *
    * @param withTxId The transaction ID to use for the input box.
    * @param withIndex The index to use for the input box.
    *
    * @return An InputBoxImpl object that represents this output as an input.
    */
  def inputBox(withTxId: String = Util.randomKey, withIndex: Short = 0): InputBoxImpl =
    this.outBox.convertToInputWith(withTxId, withIndex).asInstanceOf[InputBoxImpl]

  /**
    * Creates an OutBox from this transaction output
    *
    * @return The matching OutBox to represent this transaction output.
    */
  def outBox: OutBox = {
    var b = ctx
      .newTxBuilder() // Creating new transaction builder from ctx
      .outBoxBuilder() // Start building Outbox
      .value(value) // Setting Output's value
      .contract(contract) // Setting Output's contract

    if (tokens.size > 0) // If there are any Tokens include them
      b = b.tokens(tokens: _*)

    if (registers.size > 0) // Same thing for registers
      b = b.registers(registers: _*)

    b.build() // Return built OutBox
  }

  /**
    * Gets the list of registers of this output.
    *
    * @return The list of registers for the output.
    */
  def registers = _registers

  /**
    * Sets the list of registers for this output.
    *
    * @param newRegisters The new list of registers to set for the output.
    */
  def registers_=(newRegisters: List[ErgoValue[?]]) = _registers = newRegisters

  /**
    * Gets the value of this output.
    *
    * @return The value for the output.
    */
  def value = _value

  /**
    * Sets the value of this output.
    *
    * @param newValue The new value to set for the output.
    */
  def value_=(newValue: Long) = _value = newValue

  /**
    * Gets the contract of this output.
    *
    * @return The contract for the output.
    */
  def contract = _contract

  /**
    * Sets the contract of this output.
    *
    * @param newContract The new contract to set for the output.
    */
  def contract_=(newContract: ErgoContract) = _contract = newContract

  /**
    * Gets the list of tokens of this output.
    *
    * @return The list of tokens for the output.
    */
  def tokens = _tokens

  /**
    * Sets the list of tokens for this output.
    *
    * @param newTokens The new list of tokens to set for the output.
    */
  def tokens_=(newTokens: List[ErgoToken]) = _tokens = newTokens

  /**
    * Gets the list of context variables of this output.
    *
    * @return The list of context variables for the output.
    */
  def contextVars = _contextVars

  /**
    * Sets the list of context variables for this output.
    *
    * @param newContextVars The new list of context variables to set for the output.
    */
  def contextVars_=(newContextVars: List[ContextVar]) = _contextVars = newContextVars

  /**
    * Returns the BlockchainContextImpl instance used by this output.
    *
    * @return The context instance used by this output.
    */
  def ctx = _ctx

  /**
    * Updates the BlockchainContextImpl instance used by this output.
    *
    * @param newCtx The new context instance to be set.
    */
  def ctx_=(newCtx: BlockchainContextImpl) = _ctx = newCtx

  /**
    * Returns an ErgoTransactionOutput from the current PaideiaBox's OutBox object.
    *
    * @param withTxId the transaction ID to use for the input box
    * @param withIndex the index to use for the input box
    * @return An instance of ErgoTransactionOutput representing the current output box.
    */
  def ergoTransactionOutput(
    withTxId: String = Util.randomKey,
    withIndex: Short = 0
  ): ErgoTransactionOutput =
    ScalaBridge.isoErgoTransactionOutput.from(
      inputBox(withTxId, withIndex).asInstanceOf[InputBoxImpl].getErgoBox()
    )

  /**
    * Returns a Box object representing the current PaideiaBox's InputBoxImpl.
    *
    * @return a CostingBox initialized as false and representing the input box of this output box.
    */
  def box(): Box = CostingBox(false, inputBox().getErgoBox())

}
