package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.DAOConfig
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.DAO

class EmitTransaction extends PaideiaTransaction

object EmitTransaction {
    def apply(
        ctx: BlockchainContextImpl, 
        stakeStateInput: InputBox,
        stakingConfigInput: InputBox,
        userInput: InputBox, 
        state: TotalStakingState, 
        changeAddress: ErgoAddress,
        dao: DAO,
        stakingContract: PlasmaStaking): EmitTransaction = 
    {
        if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
        val contextVars = state.emit(ctx.createPreHeader().build().getTimestamp(),stakeStateInput.getTokens().get(1).getValue()-state.currentStakingState.totalStaked)

        val stakeStateOutput = stakingContract.box(ctx,dao.config,state,stakeStateInput.getTokens().get(1).getValue())

        val res = new EmitTransaction()
        res.ctx = ctx
        res.changeAddress = changeAddress
        res.fee = 1000000
        res.inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),userInput)
        res.dataInputs = List[InputBox](stakingConfigInput)
        res.outputs = List[OutBox](stakeStateOutput.outBox)
        res
    }
}