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
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.DAO
import im.paideia.util.ConfKeys
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.common.filtering._
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ContextVar
import org.ergoplatform.appkit.Address
import special.collection.Coll

case class UnstakeTransaction(
    _ctx: BlockchainContextImpl, 
    unstakeProxyInput: InputBox, 
    _changeAddress: ErgoAddress,
    daoKey: String) extends PaideiaTransaction {

    ctx = _ctx

    val stakingKey = unstakeProxyInput.getTokens().get(0).getId().toString()
    
    val config = Paideia.getConfig(daoKey)

    val state = TotalStakingState(daoKey)

    val removeAmount = unstakeProxyInput.getRegisters().get(1).getValue().asInstanceOf[Long].min(state.getStake(stakingKey).stake)

    val stakeStateInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid)).toString(),
        CompareField.ASSET,
        0
    ))(0)

    if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")

    val configInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        daoKey,
        CompareField.ASSET,
        0
    ))(0)

    if (configInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != config._config.ergoAVLTree.digest) throw new Exception("Config not synced correctly")
       
    val contextVars = state.unstake(stakingKey,removeAmount).::(ContextVar.of(0.toByte,config.getProof(
        ConfKeys.im_paideia_staking_emission_amount,
        ConfKeys.im_paideia_staking_emission_delay,
        ConfKeys.im_paideia_staking_cyclelength,
        ConfKeys.im_paideia_staking_profit_tokenids,
        ConfKeys.im_paideia_staking_profit_thresholds,
        ConfKeys.im_paideia_contracts_staking
    )))

    val stakingContractSignature = config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking)
    stakingContractSignature.daoKey = daoKey
    val stakingContract = PlasmaStaking(stakingContractSignature)

    val stakeStateOutput = stakingContract.box(ctx,config,state,stakeStateInput.getTokens().get(1).getValue()-removeAmount)

    val tokens = if (contextVars(1).getValue.getValue != StakingContextVars.UNSTAKE)
                    List[ErgoToken](new ErgoToken(stakingKey,1L),new ErgoToken(config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),removeAmount))
                else
                    List[ErgoToken](new ErgoToken(config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),removeAmount))

    val userOutput = ctx.newTxBuilder().outBoxBuilder().tokens(
        tokens: _*
    ).contract(Address.fromPropositionBytes(_ctx.getNetworkType(),unstakeProxyInput.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray).toErgoContract).build()

    changeAddress = _changeAddress
    fee = 1000000L
    inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),unstakeProxyInput)
    dataInputs = List[InputBox](configInput)
    outputs = List[OutBox](stakeStateOutput.outBox,userOutput)
}