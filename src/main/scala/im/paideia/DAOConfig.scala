package im.paideia

import im.paideia.util.Util
import scala.collection.mutable.HashMap
import scala.collection.mutable.Map
import special.collection.Coll
import org.ergoplatform.appkit.ErgoId
import im.paideia.common.contracts._
import org.ergoplatform.appkit.ErgoValue
import org.ergoplatform.appkit.NetworkType
import im.paideia.staking.contracts._
import io.getblok.getblok_plasma.collections.PlasmaMap
import sigmastate.AvlTreeFlags
import io.getblok.getblok_plasma.PlasmaParameters
import io.getblok.getblok_plasma.ByteConversion
import shapeless.Lazy

case class DAOConfig(
   val _config: PlasmaMap[DAOConfigKey,Array[Byte]]
) {
    def apply[T](key: String): T = DAOConfigValueDeserializer.deserialize(_config.lookUp(DAOConfigKey(key)).response.head.tryOp.get.get).asInstanceOf[T]
    def set[T](key: String, value: T)(implicit enc: DAOConfigValueSerializer[T]) = _config.insert((DAOConfigKey(key),enc.serialize(value,true).toArray))

    def handleExtension(extension: Map[String,String]) = {
        val todo = "update config based on extension"
    }
}

object DAOConfig {
    def test: DAOConfig = {
        val stakedTokenId: Array[Byte] = ErgoId.create(Util.randomKey).getBytes()
        val nftId: Array[Byte] = ErgoId.create(Util.randomKey).getBytes()
        val sigUsd: Array[Byte] = ErgoId.create(Util.randomKey).getBytes()
        val configTokenId: Array[Byte] = ErgoId.create(Util.randomKey).getBytes()
        val stakingContract = PlasmaStaking(PaideiaContractSignature())
        val daoConfig = DAOConfig()
        daoConfig.set("im.paideia.configTokenId",configTokenId)
        daoConfig.set("im.paideia.staking.nftId",nftId)
        daoConfig.set("im.paideia.staking.stakedTokenId",stakedTokenId)
        daoConfig.set("im.paideia.staking.emissionAmount",100000L)
        daoConfig.set("im.paideia.staking.emissionDelay",4L)
        daoConfig.set("im.paideia.staking.cycleLength",3600000L)
        daoConfig.set("im.paideia.staking.profitTokens",Array(stakedTokenId,sigUsd))
        daoConfig.set("im.paideia.staking.stakingContract",stakingContract.contractSignature)
        daoConfig
    }

    def apply() : DAOConfig = {
        new DAOConfig(new PlasmaMap[DAOConfigKey,Array[Byte]](AvlTreeFlags.AllOperationsAllowed,PlasmaParameters.default))
    }

    implicit val convertDAOConfigKey: ByteConversion[DAOConfigKey] = new ByteConversion[DAOConfigKey] {
        def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey
        
        def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(bytes)    
    }
}
