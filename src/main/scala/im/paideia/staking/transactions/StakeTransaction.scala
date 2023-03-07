package im.paideia.staking.transactions

import im.paideia.common.transactions._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.InputBox
import special.sigma.AvlTree
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.Eip4Token
import org.ergoplatform.appkit.OutBox
import org.ergoplatform.appkit.impl.ErgoTreeContract
import im.paideia.DAOConfig
import sigmastate.Values
import org.ergoplatform.appkit.Address
import special.collection.Coll
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.DAO
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.Paideia
import im.paideia.common.filtering._
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ErgoId
import org.ergoplatform.appkit.ContextVar

case class StakeTransaction(
    _ctx: BlockchainContextImpl, 
    stakeProxyInput: InputBox, 
    _changeAddress: ErgoAddress,
    daoKey: String) extends PaideiaTransaction {
    
    ctx = _ctx
    val amount = stakeProxyInput.getTokens().get(0).getValue()
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
    val stakeKey = stakeStateInput.getId().toString()        
    val contextVars = state.stake(stakeKey,amount).::(ContextVar.of(0.toByte,config.getProof(
        ConfKeys.im_paideia_staking_emission_amount,
        ConfKeys.im_paideia_staking_emission_delay,
        ConfKeys.im_paideia_staking_cyclelength,
        ConfKeys.im_paideia_staking_profit_tokenids,
        ConfKeys.im_paideia_staking_profit_thresholds,
        ConfKeys.im_paideia_contracts_staking
    )))

    val stakingContract = PlasmaStaking(config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking))

    val stakeStateOutput = stakingContract.box(ctx,config,state,stakeStateInput.getTokens().get(1).getValue()+amount)

    val userOutput = ctx.newTxBuilder().outBoxBuilder().mintToken(
        new Eip4Token(
            stakeKey,
            1L,
            "test",
            "test",
            0
        )
    )
    .value(1000000L)
    .contract(Address.fromPropositionBytes(ctx.getNetworkType(),stakeProxyInput.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray).toErgoContract()).build()

    fee = 1000000L
    inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),stakeProxyInput)
    dataInputs = List[InputBox](configInput)
    outputs = List[OutBox](stakeStateOutput.outBox,userOutput)
    changeAddress=_changeAddress
}