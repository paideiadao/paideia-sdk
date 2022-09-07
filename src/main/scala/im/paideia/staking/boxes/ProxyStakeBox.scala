package im.paideia.staking.boxes

import im.paideia.common.PaideiaBox
import im.paideia.staking.contracts.ProxyStake
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.ErgoToken
import im.paideia.staking.StakingConfig
import im.paideia.governance.DAOConfig
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoId

class ProxyStakeBox(nftId: String, stakeKeyTarget: Address, stakeAmount: Long) extends PaideiaBox {
    override def registers = List[ErgoValue[?]](
        ErgoValue.of(ErgoId.create(nftId).getBytes()),
        ErgoValue.of(stakeKeyTarget.toPropositionBytes()),
        ErgoValue.of(stakeAmount)
    )
}

object ProxyStakeBox {
    def apply(ctx: BlockchainContextImpl, stakingConfig: StakingConfig, daoConfig: DAOConfig, stakeKeyTarget: Address, value: Long = 1000000, stakeAmount: Long): ProxyStakeBox = {
        val res = new ProxyStakeBox(stakingConfig.nftId,stakeKeyTarget,stakeAmount)
        res.value = value
        res.contract = ProxyStake(networkType=ctx.getNetworkType()).contract
        res.tokens = List[ErgoToken](
            new ErgoToken(stakingConfig.stakedTokenId,stakeAmount)
        )
        res
    }
}