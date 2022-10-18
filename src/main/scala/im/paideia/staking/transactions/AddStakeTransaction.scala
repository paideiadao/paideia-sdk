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

case class AddStakeTransaction(
    _ctx: BlockchainContextImpl, 
    stakeStateInput: InputBox,
    configInput: InputBox,
    userInput: InputBox,
    stakingKey: String, 
    amount: Long, 
    state: TotalStakingState, 
    _changeAddress: ErgoAddress,
    daoConfig: DAOConfig,
    stakingContract: PlasmaStaking
) extends PaideiaTransaction {
    if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")
        
    val contextVars = state.addStake(stakingKey,amount)

    val stakeStateOutput = stakingContract.box(ctx,daoConfig,state,stakeStateInput.getTokens().get(1).getValue()+amount)

    val userOutput = ctx.newTxBuilder().outBoxBuilder().tokens(
        new ErgoToken(stakingKey,1L)
    ).contract(new ErgoTreeContract(userInput.getErgoTree(),ctx.getNetworkType())).build()

    ctx = _ctx
    fee = 1000000
    changeAddress = _changeAddress
    inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),userInput)
    dataInputs = List[InputBox](configInput)
    outputs = List[OutBox](stakeStateOutput.outBox,userOutput)

}