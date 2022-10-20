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
    def apply[T](key: String): T = {
        val check = _config.lookUp(DAOConfigKey(key)).response.head.tryOp.get.get
        val deserialized = DAOConfigValueDeserializer.deserialize(check)
        deserialized.asInstanceOf[T]
    }
    def set[T](key: String, value: T)(implicit enc: DAOConfigValueSerializer[T]) = _config.insert((DAOConfigKey(key),enc.serialize(value,true).toArray))

    def handleExtension(extension: Map[String,String]) = {
        val todo = "update config based on extension"
    }

    def getProof(keys: List[String]): ErgoValue[Coll[java.lang.Byte]] = {
        val provRes = _config.lookUp(keys.map(DAOConfigKey(_)): _*)
        provRes.proof.ergoValue
    }

    def insertProof(operations: List[(String,Array[Byte])]): ErgoValue[Coll[java.lang.Byte]] = {
        val provRes = _config.insert(operations.map((kv: (String,Array[Byte])) => (DAOConfigKey(kv._1),kv._2)): _*)
        provRes.proof.ergoValue
    }
}

object DAOConfig {
    def apply() : DAOConfig = {
        new DAOConfig(new PlasmaMap[DAOConfigKey,Array[Byte]](AvlTreeFlags.AllOperationsAllowed,PlasmaParameters.default))
    }

    implicit val convertDAOConfigKey: ByteConversion[DAOConfigKey] = new ByteConversion[DAOConfigKey] {
        def convertToBytes(t: DAOConfigKey): Array[Byte] = t.hashedKey
        
        def convertFromBytes(bytes: Array[Byte]): DAOConfigKey = new DAOConfigKey(bytes)    
    }
}
