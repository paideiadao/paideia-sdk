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
import im.paideia.DAO
import sigmastate.Values
import org.ergoplatform.appkit.Address
import im.paideia.common.filtering.FilterType
import im.paideia.Paideia
import im.paideia.common.filtering.FilterLeaf
import im.paideia.common.filtering.CompareField
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import im.paideia.common.contracts.PaideiaContractSignature
import special.collection.Coll

case class AddStakeTransaction(
    _ctx: BlockchainContextImpl, 
    addStakeProxyInput: InputBox,
    _changeAddress: ErgoAddress,
    daoKey: String
) extends PaideiaTransaction {
    val stakingKey = addStakeProxyInput.getTokens().get(0).getId().toString()
    val amount = addStakeProxyInput.getTokens().get(1).getValue()
    val config = Paideia.getConfig(daoKey)
    
    val state = TotalStakingState(daoKey)

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

    val contextVars = state.addStake(stakingKey,amount).::(ContextVar.of(0.toByte,config.getProof(
        ConfKeys.im_paideia_staking_emission_amount,
        ConfKeys.im_paideia_staking_emission_delay,
        ConfKeys.im_paideia_staking_cyclelength,
        ConfKeys.im_paideia_staking_profit_tokenids,
        ConfKeys.im_paideia_staking_profit_thresholds,
        ConfKeys.im_paideia_contracts_staking
    )))

    val stakingContract = PlasmaStaking(config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking))

    val stakeStateOutput = stakingContract.box(_ctx,config,state,stakeStateInput.getTokens().get(1).getValue()+amount)

    val userOutput = _ctx.newTxBuilder().outBoxBuilder().tokens(
        new ErgoToken(stakingKey,1L)
    ).contract(Address.fromPropositionBytes(_ctx.getNetworkType(),addStakeProxyInput.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray).toErgoContract).build()

    ctx = _ctx
    fee = 1000000
    changeAddress = _changeAddress
    inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),addStakeProxyInput)
    dataInputs = List[InputBox](configInput)
    outputs = List[OutBox](stakeStateOutput.outBox,userOutput)

}