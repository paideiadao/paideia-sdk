package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.ErgoToken
import im.paideia.DAOConfig
import im.paideia.staking._
import im.paideia.staking.boxes._

class UnstakeTransaction extends PaideiaTransaction {
  
}

object UnstakeTransaction {
    def apply(
        ctx: BlockchainContextImpl, 
        stakeStateInput: InputBox, 
        stakingConfigInput: InputBox,
        userInput: InputBox, 
        stakingKey: String, 
        amount: Long, 
        state: TotalStakingState, 
        changeAddress: ErgoAddress,
        daoConfig: DAOConfig): UnstakeTransaction = 
    {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val contextVars = state.unstake(stakingKey,amount)

        val stakeStateOutput = StakeStateBox(ctx,state,stakeStateInput.getTokens().get(1).getValue()-amount,daoConfig)

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
        res.dataInputs = List[InputBox](stakingConfigInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox,userOutput)
        res
    }
}