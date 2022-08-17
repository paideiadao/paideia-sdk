package im.paideia.staking

import im.paideia.common.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract

class StakeTransaction extends PaideiaTransaction {
  
}

object StakeTransaction {
    def apply(ctx: BlockchainContextImpl, stakeStateInput: InputBox, userInput: InputBox, amount: Long, state: TotalStakingState, changeAddress: ErgoAddress): StakeTransaction = {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val contextVars = state.stake(stakeStateInput.getId().toString(),amount)

        val stakeStateOutput = StakeStateBox(ctx,state,stakeStateInput.getTokens().get(1).getValue()+amount)
      
        val userOutput = ctx.newTxBuilder().outBoxBuilder().mintToken(
            new Eip4Token(
                stakeStateInput.getId().toString(),
                1L,
                "test",
                "test",
                0
            )
        ).contract(new ErgoTreeContract(userInput.getErgoTree(),ctx.getNetworkType())).build()

        val res = new StakeTransaction()
        res.ctx = ctx
        res.changeAddress = changeAddress
        res.fee = 1000000
        res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),userInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox(ctx),userOutput)
        res
    }
}