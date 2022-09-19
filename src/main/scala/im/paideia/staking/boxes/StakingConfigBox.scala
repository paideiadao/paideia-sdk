package im.paideia.staking.boxes

import org.ergoplatform.appkit.impl.InputBoxImpl
import im.paideia.common.boxes._
import im.paideia.staking._
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
import im.paideia.DAOConfig
import special.sigma.SigmaDsl
import scorex.crypto.hash.Blake2b256
import org.ergoplatform.appkit.ErgoId

class StakingConfigBox(ctx: BlockchainContextImpl, stakingConfig:StakingConfig, stakingErgoTree: Values.ErgoTree) extends ConfigBox(ctx,ConfigBox.stakingConfigIndex) {
    override def registers = List[ErgoValue[?]](
        ErgoValue.of(Array(
            ConfigBox.stakingConfigIndex,
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

    def apply(ctx: BlockchainContextImpl, stakingConfig: StakingConfig, daoConfig: DAOConfig): StakingConfigBox = {
        val tree = DAOControlled(
            constants=Map(
                "_configIndex" -> ErgoValue.of(ConfigBox.stakingConfigIndex).getValue(),
                "_configTokenId" -> ErgoValue.of(ErgoId.create(daoConfig("configTokenId")).getBytes()).getValue()),
            networkType=ctx.getNetworkType(),
            script=PlasmaStaking(networkType=ctx.getNetworkType()).ergoScript).ergoTree
        val res = new StakingConfigBox(ctx,stakingConfig,tree)
        res.tokens = List[ErgoToken](
            new ErgoToken(daoConfig[String]("configTokenId"),1L)
        )
        res
    }
}
