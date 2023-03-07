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
import im.paideia.DAOConfig
import org.ergoplatform.appkit.ErgoId
import sigmastate.utils.Helpers
import sigmastate.Values
import im.paideia.staking._
import im.paideia.common.boxes._
import sigmastate.eval.Colls
import org.ergoplatform.appkit.scalaapi.ErgoValueBuilder

class StakeStateBox(val state:TotalStakingState) extends PaideiaBox {
    override def registers = List[ErgoValue[?]](
        state.currentStakingState.plasmaMap.ergoValue,
        ErgoValueBuilder.buildFor(Colls.fromArray(Array(
            state.nextEmission,
            state.currentStakingState.size.toLong,
            state.currentStakingState.totalStaked
        ).++(state.profit))),
        ErgoValueBuilder.buildFor(Colls.fromArray(state.snapshots.toArray.map(
            (kv: (Long,StakingState,List[Long])) => 
                        kv._1
                    ))),
        ErgoValueBuilder.buildFor(Colls.fromArray(state.snapshots.toArray.map(
            (kv: (Long,StakingState,List[Long])) => 
                        kv._2.plasmaMap.ergoValue.getValue()
                    ))),
        ErgoValueBuilder.buildFor(Colls.fromArray(state.snapshots.toArray.map(
            (kv: (Long,StakingState,List[Long])) => 
                        Colls.fromArray(kv._3.toArray)
                    )))
    )
}

