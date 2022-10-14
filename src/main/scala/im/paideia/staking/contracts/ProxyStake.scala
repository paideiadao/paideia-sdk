package im.paideia.staking.contracts

import org.ergoplatform.appkit.NetworkType
import im.paideia.common.contracts._
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import im.paideia.DAOConfig
import org.ergoplatform.appkit.Address
import im.paideia.staking.boxes.ProxyStakeBox
import org.ergoplatform.appkit.ErgoToken

class ProxyStake(contractSignature: PaideiaContractSignature) extends PaideiaContract(contractSignature) {
    def box(ctx: BlockchainContextImpl, daoConfig: DAOConfig, stakeKeyTarget: Address, value: Long = 1000000, stakeAmount: Long): ProxyStakeBox = {
        val res = new ProxyStakeBox(daoConfig[String]("im.paideia.staking.nftId"),stakeKeyTarget,stakeAmount)
        res.value = value
        res.ctx = ctx
        res.contract = contract
        res.tokens = List[ErgoToken](
            new ErgoToken(daoConfig[Array[Byte]]("im.paideia.staking.stakedTokenId"),stakeAmount)
        )
        res
    }
}

object ProxyStake extends PaideiaActor {
    override def apply(contractSignature: PaideiaContractSignature): ProxyStake = getContractInstance[ProxyStake](contractSignature,new ProxyStake(contractSignature))
}