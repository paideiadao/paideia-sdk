package im.paideia.common.boxes

import sigmastate.Values
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.ErgoContract
import org.ergoplatform.restapi.client.ErgoTransactionOutput
import org.ergoplatform.appkit.impl.ScalaBridge
import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.util.Util
import special.sigma.Box
import sigmastate.eval.CostingBox

trait PaideiaBox {
    private var _value: Long = _
    private var _contract: ErgoContract = _
    private var _tokens: List[ErgoToken] = List[ErgoToken]()
    private var _registers: List[ErgoValue[_]] = List[ErgoValue[_]]()
    private var _contextVars: List[ContextVar] = _
    private var _ctx : BlockchainContextImpl = _

    def inputBox(withTxId: String = Util.randomKey, withIndex: Short = 0): InputBoxImpl = this.outBox.convertToInputWith(withTxId,withIndex).asInstanceOf[InputBoxImpl]
    def outBox: OutBox = {
        var b = ctx.newTxBuilder().outBoxBuilder()
            .value(value)
            .contract(contract)
        if (tokens.size>0) b = b.tokens(tokens: _*)
        if (registers.size>0) b = b.registers(registers: _*)
        b.build()
    }

    def registers = _registers
    def registers_= (newRegisters: List[ErgoValue[?]]) = _registers = newRegisters

    def value = _value
    def value_= (newValue: Long) = _value = newValue

    def contract = _contract
    def contract_= (newContract: ErgoContract) = _contract = newContract

    def tokens = _tokens
    def tokens_= (newTokens: List[ErgoToken]) = _tokens = newTokens

    def contextVars = _contextVars
    def contextVars_= (newContextVars: List[ContextVar]) = _contextVars = newContextVars

    def ctx = _ctx
    def ctx_= (newCtx: BlockchainContextImpl) = _ctx = newCtx

    def ergoTransactionOutput(withTxId: String = Util.randomKey, withIndex: Short = 0): ErgoTransactionOutput = ScalaBridge.isoErgoTransactionOutput.from(inputBox(withTxId,withIndex).asInstanceOf[InputBoxImpl].getErgoBox())

    def box(): Box = CostingBox(false,inputBox().getErgoBox())
}
