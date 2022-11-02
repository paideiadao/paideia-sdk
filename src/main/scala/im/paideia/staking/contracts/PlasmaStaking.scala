package im.paideia.staking.contracts

import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts._
import sigmastate.Values
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import im.paideia.staking.TotalStakingState
import org.ergoplatform.appkit.ErgoToken
import im.paideia.staking.boxes.StakeStateBox
import java.util.HashMap
import org.ergoplatform.appkit.ErgoId
import im.paideia.util.ConfKeys
import im.paideia.DAO

class PlasmaStaking(contractSig: PaideiaContractSignature) extends PaideiaContract(contractSig) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig, state: TotalStakingState, stakedTokenTotal: Long, value: Long = 1000000, extraTokens: List[ErgoToken] = List[ErgoToken]()): StakeStateBox = {
        val res = new StakeStateBox(state)
        res.value = value
        res.ctx = ctx
        res.contract = contract
        res.tokens = List[ErgoToken](
            new ErgoToken(daoConfig.getArray[Byte](ConfKeys.im_paideia_staking_state_tokenid),1L),
            new ErgoToken(daoConfig.getArray[Byte](ConfKeys.im_paideia_dao_tokenid),stakedTokenTotal)
        ) ++ extraTokens
        res
    }

    override def constants: HashMap[String,Object] = {
        val cons = new HashMap[String,Object]()
        cons.put("_IM_PAIDEIA_DAO_KEY",ErgoId.create(contractSig.daoKey).getBytes())
        cons.put("_IM_PAIDEIA_STAKING_EMISSION_AMOUNT",ConfKeys.im_paideia_staking_emission_amount.ergoValue.getValue())
        cons.put("_IM_PAIDEIA_STAKING_EMISSION_DELAY",ConfKeys.im_paideia_staking_emission_delay.ergoValue.getValue())   
        cons.put("_IM_PAIDEIA_STAKING_CYCLELENGTH",ConfKeys.im_paideia_staking_cyclelength.ergoValue.getValue()) 
        cons.put("_IM_PAIDEIA_STAKING_PROFIT_TOKENIDS",ConfKeys.im_paideia_staking_profit_tokenids.ergoValue.getValue())   
        cons.put("_IM_PAIDEIA_STAKING_PROFIT_THRESHOLDS",ConfKeys.im_paideia_staking_profit_thresholds.ergoValue.getValue())
        cons.put("_IM_PAIDEIA_CONTRACTS_STAKING",ConfKeys.im_paideia_contracts_staking.ergoValue.getValue())
        cons
    }
}


object PlasmaStaking extends PaideiaActor {
   override def apply(contractSignature: PaideiaContractSignature): PlasmaStaking = getContractInstance[PlasmaStaking](contractSignature,new PlasmaStaking(contractSignature))
}