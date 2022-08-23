package im.paideia.staking

import im.paideia.common.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.governance.DAOConfig

class CompoundTransaction extends PaideiaTransaction {
  
}

object CompoundTransaction {
    def apply(
        ctx: BlockchainContextImpl, 
        stakeStateInput: InputBox,
        stakingConfigInput: InputBox,
        userInput: InputBox, 
        amount: Int, 
        state: TotalStakingState, 
        changeAddress: ErgoAddress,
        daoConfig: DAOConfig): CompoundTransaction = 
    {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val contextVars = state.compound(amount)

        val stakeStateOutput = StakeStateBox(ctx,state,stakeStateInput.getTokens().get(1).getValue(),daoConfig)

        val res = new CompoundTransaction()
        res.ctx = ctx
        res.changeAddress = changeAddress
        res.fee = 1000000
        res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),userInput)
        res.dataInputs = List[InputBox](stakingConfigInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox(ctx))
        res
    }
}