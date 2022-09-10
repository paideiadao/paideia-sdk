package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.common.boxes.PaideiaBox
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
import im.paideia.common.contracts.DAOControlled
import im.paideia.governance.DAOConfig
import org.ergoplatform.appkit.ErgoId
import sigmastate.utils.Helpers
import sigmastate.Values
import im.paideia.staking._
import im.paideia.common.boxes._

class StakeStateBox(val state:TotalStakingState) extends PaideiaBox {
    override def registers = List[ErgoValue[?]](
        state.currentStakingState.plasmaMap.ergoValue,
        ErgoValue.of(Array(
            state.nextEmission,
            state.currentStakingState.size.toLong,
            state.currentStakingState.totalStaked
        ).++(state.profit).map(java.lang.Long.valueOf),ErgoType.longType()),
        ErgoValue.of(this.state.snapshots.toArray.map(
            (kv: (Long,StakingState,List[Long])) => 
                        java.lang.Long.valueOf(kv._1)
                    ),ErgoType.longType()),
        ErgoValue.of(this.state.snapshots.toArray.map(
            (kv: (Long,StakingState,List[Long])) => 
                        kv._2.plasmaMap.ergoValue.getValue()
                    ),ErgoType.avlTreeType()),
        ErgoValue.of(this.state.snapshots.toArray.map(
            (kv: (Long,StakingState,List[Long])) => 
                        ErgoValue.of(kv._3.toArray.map(java.lang.Long.valueOf),ErgoType.longType()).getValue()
                    ),ErgoType.collType(ErgoType.longType()))
    )
}

object StakeStateBox {
    def apply(ctx: BlockchainContextImpl, state: TotalStakingState, stakedTokenTotal: Long, daoConfig: DAOConfig, value: Long = 1000000, extraTokens: List[ErgoToken] = List[ErgoToken]()): StakeStateBox = {
        val script = PlasmaStaking(networkType=ctx.getNetworkType()).ergoScript
        val res = new StakeStateBox(state)
        res.value = value
        res.ctx = ctx
        res.contract = DAOControlled(
            constants=Map(
                "_configIndex" -> ErgoValue.of(ConfigBox.stakingConfigIndex).getValue(),
                "_configTokenId" -> ErgoValue.of(ErgoId.create(daoConfig.configTokenId).getBytes()).getValue()),
            networkType=ctx.getNetworkType(),
            script=script).contract
        res.tokens = List[ErgoToken](
            new ErgoToken(state.stakingConfig.nftId,1L),
            new ErgoToken(state.stakingConfig.stakedTokenId,stakedTokenTotal)
        ) ++ extraTokens
        res
    }
}
