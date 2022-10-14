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

class PlasmaStaking(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig, state: TotalStakingState, stakedTokenTotal: Long, value: Long = 1000000, extraTokens: List[ErgoToken] = List[ErgoToken]()): StakeStateBox = {
        val res = new StakeStateBox(state)
        res.value = value
        res.ctx = ctx
        res.contract = contract
        res.tokens = List[ErgoToken](
            new ErgoToken(daoConfig[Array[Byte]]("im.paideia.staking.nftId"),1L),
            new ErgoToken(daoConfig[Array[Byte]]("im.paideia.staking.stakedTokenId"),stakedTokenTotal)
        ) ++ extraTokens
        res
    } 
}


object PlasmaStaking extends PaideiaActor {
   override def apply(contractSignature: PaideiaContractSignature): PlasmaStaking = getContractInstance[PlasmaStaking](contractSignature,new PlasmaStaking(contractSignature))
}