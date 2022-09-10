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

trait PaideiaBox {
    private var _value: Long = _
    private var _contract: ErgoContract = _
    private var _tokens: List[ErgoToken] = _
    private var _registers: List[ErgoValue[_]] = _
    private var _contextVars: List[ContextVar] = _
    private var _ctx : BlockchainContextImpl = _

    def inputBox(withTxId: String = "ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", withIndex: Short = 0): InputBox = this.outBox.convertToInputWith(withTxId,withIndex)
    def outBox: OutBox = {
        ctx.newTxBuilder().outBoxBuilder()
            .value(value)
            .contract(contract)
            .tokens(tokens: _*)
            .registers(registers: _*)
            .build()
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
}
