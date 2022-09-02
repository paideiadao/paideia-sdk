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
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.JavaHelpers
import sigmastate.Values
import im.paideia.common.contracts.Config
import im.paideia.common.contracts.DAOControlled
import im.paideia.governance.DAOConfig
import special.sigma.SigmaDsl
import scorex.crypto.hash.Blake2b256
import org.ergoplatform.appkit.ErgoId

class StakingConfigBox(val stakingConfig:StakingConfig, stakingErgoTree: Values.ErgoTree) extends PaideiaBox {
    override def registers = List[ErgoValue[?]](
        ErgoValue.of(Array(
            StakingConfigBox.configIndex,
            stakingConfig.emissionAmount,
            stakingConfig.emissionDelay,
            stakingConfig.cycleLength
        ).map(java.lang.Long.valueOf),ErgoType.longType()),
        ErgoValue.of(Blake2b256(stakingErgoTree.bytes).array),
        ErgoValue.of(stakingConfig.profitTokens.map((pt: (String,Long)) => 
            ErgoValue.pairOf(ErgoValue.of(ErgoId.create(pt._1).getBytes()),ErgoValue.of(pt._2)).getValue).toArray,ErgoType.pairType(ErgoType.collType(ErgoType.byteType()),ErgoType.longType()))
    )
}

object StakingConfigBox {

    val configIndex = 1L

    def apply(ctx: BlockchainContextImpl, stakingConfig: StakingConfig, daoConfig: DAOConfig): StakingConfigBox = {
        val tree = DAOControlled(
            constants=Map(
                "_configIndex" -> ErgoValue.of(StakingConfigBox.configIndex).getValue(),
                "_configTokenId" -> ErgoValue.of(ErgoId.create(daoConfig.configTokenId).getBytes()).getValue()),
            networkType=ctx.getNetworkType(),
            script=PlasmaStaking(networkType=ctx.getNetworkType()).ergoScript).ergoTree
        val res = new StakingConfigBox(stakingConfig,tree)
        res.value = 1000000
        res.contract = Config(networkType=ctx.getNetworkType()).contract
        res.tokens = List[ErgoToken](
            new ErgoToken(daoConfig.configTokenId,1L)
        )
        res
    }
}
