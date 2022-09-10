package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.governance.DAOConfig
import sigmastate.Values
import org.ergoplatform.appkit.Address
import special.collection.Coll
import im.paideia.staking._
import im.paideia.staking.boxes._

class StakeTransaction extends PaideiaTransaction {
  
}

object StakeTransaction {
    def apply(
        ctx: BlockchainContextImpl, 
        stakeStateInput: InputBox, 
        stakingConfigInput: InputBox,
        proxyInput: InputBox, 
        amount: Long, 
        state: TotalStakingState, 
        changeAddress: ErgoAddress,
        daoConfig: DAOConfig): StakeTransaction = 
    {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val contextVars = state.stake(stakeStateInput.getId().toString(),amount)

        val stakeStateOutput = StakeStateBox(ctx,state,stakeStateInput.getTokens().get(1).getValue()+amount,daoConfig)
      
        val userOutput = ctx.newTxBuilder().outBoxBuilder().mintToken(
            new Eip4Token(
                stakeStateInput.getId().toString(),
                1L,
                "test",
                "test",
                0
            )
        ).contract(Address.fromPropositionBytes(ctx.getNetworkType(),proxyInput.getRegisters().get(1).getValue().asInstanceOf[Coll[Byte]].toArray).toErgoContract()).build()
        val reg = stakeStateOutput.registers
        val res = new StakeTransaction()
        res.ctx = ctx
        res.changeAddress = changeAddress
        res.fee = 1000000
        res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),proxyInput)
        res.dataInputs = List[InputBox](stakingConfigInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox,userOutput)
        res
    }
}