package im.paideia.staking

import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.common.PaideiaBox
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.ErgoType
import org.ergoplatform.appkit.InputBox
import org.ergoplatform.appkit.OutBox
import special.collection.Coll
import special.collection.CollBuilder
import special.collection.CollOverArray
import scala.collection.JavaConverters._
import scala.collection.mutable.Buffer
import org.ergoplatform.appkit.impl.BlockchainContextImpl
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.ContextVar
import im.paideia.staking.contracts.PlasmaStaking
import org.ergoplatform.appkit.ErgoToken

class StakeStateBox(val state:TotalStakingState) extends PaideiaBox {
    override def registers = List[ErgoValue[?]](
        state.currentStakingState.plasmaMap.ergoValue,
        ErgoValue.of(Array(
            state.stakingConfig.emissionAmount,
            state.stakingConfig.emissionDelay,
            state.stakingConfig.cycleLength,
            state.nextEmission,
            state.currentStakingState.size.toLong,
            state.currentStakingState.totalStaked
        ).map(java.lang.Long.valueOf),ErgoType.longType()),
        ErgoValue.of(this.state.snapshots.toArray.map(
            (kv: (Long,StakingState)) => 
                        java.lang.Long.valueOf(kv._1)
                    ),ErgoType.longType()),
        ErgoValue.of(this.state.snapshots.toArray.map(
            (kv: (Long,StakingState)) => 
                        kv._2.plasmaMap.ergoValue.getValue()
                    ),ErgoType.avlTreeType())
    )
}

object StakeStateBox {
    def apply(ctx: BlockchainContextImpl, state: TotalStakingState, stakedTokenTotal: Long): StakeStateBox = {
        val res = new StakeStateBox(state)
        res.value = 1000000
        res.contract = PlasmaStaking(networkType=ctx.getNetworkType()).contract
        res.tokens = List[ErgoToken](
            new ErgoToken(state.stakingConfig.nftId,1L),
            new ErgoToken(state.stakingConfig.stakedTokenId,stakedTokenTotal)
        )
        res
    }
}
