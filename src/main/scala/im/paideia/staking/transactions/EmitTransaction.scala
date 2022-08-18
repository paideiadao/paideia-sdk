package im.paideia.staking.transactions

import im.paideia.common.PaideiaTransaction
import im.paideia.common.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.staking.StakeStateBox
import im.paideia.staking.TotalStakingState

class EmitTransaction extends PaideiaTransaction

object EmitTransaction {
    def apply(ctx: BlockchainContextImpl, stakeStateInput: InputBox, userInput: InputBox, state: TotalStakingState, changeAddress: ErgoAddress): EmitTransaction = {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val contextVars = state.emit(ctx.createPreHeader().build().getTimestamp())

        val stakeStateOutput = StakeStateBox(ctx,state,stakeStateInput.getTokens().get(1).getValue())

        val res = new EmitTransaction()
        res.ctx = ctx
        res.changeAddress = changeAddress
        res.fee = 1000000
        res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),userInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox(ctx))
        res
    }
}