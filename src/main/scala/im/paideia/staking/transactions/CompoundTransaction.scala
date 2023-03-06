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
import im.paideia.staking._
import im.paideia.staking.boxes._
import im.paideia.staking.contracts.PlasmaStaking
import im.paideia.DAO
import im.paideia.Paideia
import im.paideia.common.filtering._
import im.paideia.common.contracts.OperatorIncentive
import im.paideia.common.contracts.PaideiaContractSignature
import im.paideia.util.Env
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import org.ergoplatform.appkit.ContextVar

case class CompoundTransaction(
        _ctx: BlockchainContextImpl,
        stakeStateInput: InputBox,
        _changeAddress: ErgoAddress,
        daoKey: String
    ) extends PaideiaTransaction 
{
    ctx = _ctx
    
    val config = Paideia.getConfig(daoKey)

    val state = TotalStakingState(daoKey)

    val incentiveInput = Paideia.getBox(new FilterNode(
        FilterType.FTALL,
        List(
            new FilterLeaf[String](
                FilterType.FTEQ,
                OperatorIncentive(PaideiaContractSignature(daoKey = Env.paideiaDaoKey)).ergoTree.bytesHex,
                CompareField.ERGO_TREE,
                0
            ),
            new FilterLeaf[Long](
                FilterType.FTGT,
                1000000L,
                CompareField.VALUE,
                0
            )
        )))(0)

    if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")

    val configInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        daoKey,
        CompareField.ASSET,
        0
    ))(0)

    if (configInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != config._config.ergoAVLTree.digest) throw new Exception("Config not synced correctly")
       
    val contextVars = state.compound(Env.compoundBatchSize).::(ContextVar.of(0.toByte,config.getProof(
        ConfKeys.im_paideia_staking_emission_amount,
        ConfKeys.im_paideia_staking_emission_delay,
        ConfKeys.im_paideia_staking_cyclelength,
        ConfKeys.im_paideia_staking_profit_tokenids,
        ConfKeys.im_paideia_staking_profit_thresholds,
        ConfKeys.im_paideia_contracts_staking
    )))
    val stakingContract = PlasmaStaking(config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking))
    val stakeStateOutput = stakingContract.box(ctx,config,state,stakeStateInput.getTokens().get(1).getValue())

    changeAddress = _changeAddress
    fee = 1000000L
    inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),incentiveInput)
    dataInputs = List[InputBox](configInput)
    outputs = List[OutBox](stakeStateOutput.outBox)
}