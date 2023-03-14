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
import scala.collection.JavaConverters._

case class UnstakeTransaction(
    _ctx: BlockchainContextImpl, 
    unstakeProxyInput: InputBox, 
    _changeAddress: ErgoAddress,
    daoKey: String) extends PaideiaTransaction {

    ctx = _ctx

    val stakingKey = unstakeProxyInput.getTokens().get(0).getId().toString()
    
    val config = Paideia.getConfig(daoKey)

    val state = TotalStakingState(daoKey)

    val newStakeRecord = StakeRecord.stakeRecordConversion.convertFromBytes(unstakeProxyInput.getRegisters().get(1).getValue().asInstanceOf[Coll[Byte]].toArray)
    val currentStakeRecord = state.getStake(stakingKey)

    val whiteListedTokens = config.getArray[Object](ConfKeys.im_paideia_staking_profit_tokenids).map(
        (arrB: Object) =>
            new ErgoId(arrB.asInstanceOf[Array[Object]].map(_.asInstanceOf[Byte]))
    )

    val stakeStateInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        new ErgoId(config.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid)).toString(),
        CompareField.ASSET,
        0
    ))(0)

    val stakeStateInputBox = StakeStateBox.fromInputBox(ctx, stakeStateInput)

    if (stakeStateInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != state.currentStakingState.plasmaMap.ergoAVLTree.digest) throw new Exception("State not synced correctly")

    val configInput = Paideia.getBox(new FilterLeaf[String](
        FilterType.FTEQ,
        daoKey,
        CompareField.ASSET,
        0
    ))(0)

    if (configInput.getRegisters().get(0).getValue.asInstanceOf[AvlTree].digest != config._config.ergoAVLTree.digest) throw new Exception("Config not synced correctly")
       
    val contextVars = state.unstake(stakingKey,newStakeRecord).::(ContextVar.of(0.toByte,config.getProof(
        ConfKeys.im_paideia_staking_emission_amount,
        ConfKeys.im_paideia_staking_emission_delay,
        ConfKeys.im_paideia_staking_cyclelength,
        ConfKeys.im_paideia_staking_profit_tokenids,
        ConfKeys.im_paideia_staking_profit_thresholds,
        ConfKeys.im_paideia_contracts_staking
    )))

    val proxyContextVars = List(
        ContextVar.of(0.toByte, config.getProof(
            ConfKeys.im_paideia_staking_state_tokenid,
            ConfKeys.im_paideia_staking_profit_tokenids
        )),
        ContextVar.of(1.toByte, contextVars(3).getValue()),
        ContextVar.of(2.toByte, contextVars(4).getValue())
    )

    val stakingContractSignature = config[PaideiaContractSignature](ConfKeys.im_paideia_contracts_staking)
    stakingContractSignature.daoKey = daoKey
    val stakingContract = PlasmaStaking(stakingContractSignature)

    val stakeStateOutput = stakingContract.box(
        ctx,
        daoKey,
        stakeStateInputBox.stakedTokenTotal-(currentStakeRecord.stake-newStakeRecord.stake),
        stakeStateInputBox.extraTokens.map{
            (et: ErgoToken) =>
                val stakeRecordIndex = whiteListedTokens.indexOf(et.getId())
                new ErgoToken(et.getId(),et.getValue()-(currentStakeRecord.rewards(1+stakeRecordIndex)-newStakeRecord.rewards(1+stakeRecordIndex)))
        }.filter((et: ErgoToken) => et.getValue()>0L).toList)

    val govTokenUnstake = if (currentStakeRecord.stake > newStakeRecord.stake) {
        List(new ErgoToken(config.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),currentStakeRecord.stake-newStakeRecord.stake))
    } else {
        List[ErgoToken]()
    }

    val profitTokenUnstake = currentStakeRecord.rewards.indices.slice(0,currentStakeRecord.rewards.size-1).map((i: Int) => 
        if (currentStakeRecord.rewards(i+1)-newStakeRecord.rewards(i+1) > 0) {
            Some(new ErgoToken(whiteListedTokens(i),currentStakeRecord.rewards(i+1)-newStakeRecord.rewards(i+1)))
        } else {
            None
        }
    ).flatten.toList

    val stakeKeyReturned = if (contextVars(1).getValue.getValue != StakingContextVars.UNSTAKE)
                    List[ErgoToken](new ErgoToken(stakingKey,1L))
                else
                    List[ErgoToken]()

    val tokens = stakeKeyReturned++govTokenUnstake++profitTokenUnstake

    val userOutput = ctx.newTxBuilder().outBoxBuilder().value(1000000L+currentStakeRecord.rewards(0)-newStakeRecord.rewards(0)).tokens(
        tokens: _*
    ).contract(Address.fromPropositionBytes(_ctx.getNetworkType(),unstakeProxyInput.getRegisters().get(0).getValue().asInstanceOf[Coll[Byte]].toArray).toErgoContract).build()

    changeAddress = _changeAddress
    fee = 1000000L
    inputs = List[InputBox](stakeStateInput.withContextVars(contextVars: _*),unstakeProxyInput.withContextVars(proxyContextVars:_*))
    dataInputs = List[InputBox](configInput)
    outputs = List[OutBox](stakeStateOutput.outBox,userOutput)
}