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
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.ErgoValue
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.staking._
import im.paideia.staking.contracts._
import im.paideia.staking.boxes._

class AddStakeTransaction extends PaideiaTransaction {
  
}

object AddStakeTransaction {
    def apply(
        ctx: BlockchainContextImpl, 
        stakeStateInput: InputBox,
        configInput: InputBox,
        userInput: InputBox,
        stakingKey: String, 
        amount: Long, 
        state: TotalStakingState, 
        changeAddress: ErgoAddress,
        daoConfig: DAOConfig,
        stakingContract: PlasmaStaking): AddStakeTransaction = 
    {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val contextVars = state.addStake(stakingKey,amount)

        val stakeStateOutput = stakingContract.box(ctx,daoConfig,state,stakeStateInput.getTokens().get(1).getValue()+amount)

        val userOutput = ctx.newTxBuilder().outBoxBuilder().tokens(
            new ErgoToken(stakingKey,1L)
        ).contract(new ErgoTreeContract(userInput.getErgoTree(),ctx.getNetworkType())).build()

        val res = new AddStakeTransaction()
        res.ctx = ctx
        res.changeAddress = changeAddress
        res.fee = 1000000
        res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),userInput)
        res.dataInputs = List[InputBox](configInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox,userOutput)
        res
    }
}