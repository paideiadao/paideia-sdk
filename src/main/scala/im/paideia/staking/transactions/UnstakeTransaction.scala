package im.paideia.staking

import im.paideia.common.PaideiaTransaction
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.ErgoToken

class UnstakeTransaction extends PaideiaTransaction {
  
}

object UnstakeTransaction {
    def apply(ctx: BlockchainContextImpl, stakeStateInput: InputBox, userInput: InputBox, stakingKey: String, amount: Long, state: TotalStakingState, changeAddress: ErgoAddress): UnstakeTransaction = {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val contextVars = state.unstake(stakingKey,amount)

        val stakeStateOutput = StakeStateBox(ctx,state,stakeStateInput.getTokens().get(1).getValue()-amount)

        val tokens = if (contextVars(0).getValue.getValue != StakingContextVars.UNSTAKE)
                        List[ErgoToken](new ErgoToken(stakingKey,1L),new ErgoToken(state.stakingConfig.stakedTokenId,amount))
                    else
                        List[ErgoToken](new ErgoToken(state.stakingConfig.stakedTokenId,amount))

        val userOutput = ctx.newTxBuilder().outBoxBuilder().tokens(
            tokens: _*
        ).contract(new ErgoTreeContract(userInput.getErgoTree(),ctx.getNetworkType())).build()

        val res = new UnstakeTransaction()
        res.ctx = ctx
        res.changeAddress = changeAddress
        res.fee = 1000000
        res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),userInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox(ctx),userOutput)
        res
    }
}